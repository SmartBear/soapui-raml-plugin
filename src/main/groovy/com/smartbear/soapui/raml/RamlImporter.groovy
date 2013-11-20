/**
 *  Copyright 2013 SmartBear Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.smartbear.soapui.raml

import com.esotericsoftware.yamlbeans.YamlReader
import com.eviware.soapui.impl.rest.RestMethod
import com.eviware.soapui.impl.rest.RestRepresentation
import com.eviware.soapui.impl.rest.RestRequestInterface.RequestMethod
import com.eviware.soapui.impl.rest.RestResource
import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.rest.RestServiceFactory
import com.eviware.soapui.impl.rest.support.RestParameter
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder.ParameterStyle
import com.eviware.soapui.impl.rest.support.RestUtils
import com.eviware.soapui.impl.wsdl.WsdlProject
import com.eviware.soapui.impl.wsdl.support.PathUtils
import org.apache.xmlbeans.XmlBoolean
import org.apache.xmlbeans.XmlDate
import org.apache.xmlbeans.XmlDouble
import org.apache.xmlbeans.XmlInteger
import org.apache.xmlbeans.XmlString

/**
 * A simple RAML importer
 * 
 * @author Ole Lensmar
 */

class RamlImporter {

    private static final String MEDIA_TYPE_EXTENSION = "{mediaTypeExtension}"
    private final WsdlProject project
    private String defaultMediaType
    private String defaultMediaTypeExtension
    private def baseUriParams = [:]
    private def resourceTypes = [:]
    private def traits = [:]

    public RamlImporter( WsdlProject project ) {
		this.project = project
	}

	public RestService importRaml( String url ) {

        String txt = new URL( url ).openStream().text

        txt = expandIncludes( txt, url )

        def reader = new YamlReader( txt )
        def raml = reader.read()
        def service = createRestService( raml )

        baseUriParams = extractUriParams( raml.baseUri, raml.baseUriParameters )
        if( baseUriParams.version != null )
            baseUriParams.version.default = raml.version

        // extract default media type
        if( raml.mediaType != null )
        {
            defaultMediaType = raml.mediaType
            defaultMediaTypeExtension = defaultMediaType
            if( defaultMediaTypeExtension.contains( '/'))
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring( defaultMediaTypeExtension.lastIndexOf( '/' )+1 )
            if( defaultMediaTypeExtension.contains( '-'))
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring( defaultMediaTypeExtension.lastIndexOf( '-' )+1 )
        }

        raml.resourceTypes?.each{
            it.each{
                resourceTypes[it.key] = it.value
            }
        }

        raml.traits?.each{
            it.each{
                traits[it.key] = it.value
            }
        }

        raml.each {
            if( it.key.startsWith( '/'))
            {
                addResource( service, it )
            }
        }

		return service
	}

    String expandIncludes(String s, String path) {
        def ix = s.indexOf( "!include ")
        while( ix >= 0 )
        {
            def endIx = ix + 8
            while( ++endIx < s.length() )
            {
                if( s.charAt(endIx).isWhitespace() )
                    break
            }

            // only supports relative paths for now
            def file = s.substring( ix+9, endIx )
            def ix2 = path.lastIndexOf( '/' )
            def newPath = path.substring(0, ix2+1 ) + file
            try {
                def inc = new URL( newPath ).text
                ix2 = inc.indexOf( "---")
                if( ix2 > 0 )
                    inc = inc.substring(ix2+3).trim()

                s = s.substring( 0, ix ) + inc + s.substring(endIx)
            }
            catch( e )
            {
              //  Console.println( "Error expanding include: " + e.message)
                s = s.substring( 0, ix ) + s.substring(endIx)
            }

            ix = s.indexOf( "!include ")
        }

        return s
    }

    def addResource(RestService service, def it ) {

        def resource = service.addNewResource( getResourceName( it ), it.key )
        initResource( resource, it, it.value.is )

        def traits = it.value.is
        it.value.each {
            if( it.key.startsWith( '/'))
            {
                addChildResource( resource, it, traits )
            }
        }
    }

    String getResourceName(def it) {
        def name = it.value.displayName
        if( name == null )
            name = it.key

        if( name.endsWith(MEDIA_TYPE_EXTENSION))
            name = name.substring( 0, name.length()-20)

        return name
    }

    def addChildResource(RestResource resource, def it, def resourceTraits)
    {
        def childResource = resource.addNewChildResource( getResourceName( it ), it.key )
        def traits = it.value.is == null ? resourceTraits : it.value.is + resourceTraits

        initResource( childResource, it, traits )

        if( baseUriParams.version != null )
            childResource.params.removeProperty( "version")

        it.value.each {
            if( it.key.startsWith( '/'))
            {
                addChildResource( childResource, it, traits )
            }
        }
    }

    def initResource(RestResource resource, def res, def resourceTraits ) {

        //Console.println( "Initializing resource with path " + restResource.fullPath )

        if( resource.description != null )
            resource.description = res.value.description

        if( res.key.contains(MEDIA_TYPE_EXTENSION) )
        {
            RestParameter p = resource.params.addProperty( "mediaTypeExtension" )
            p.style = ParameterStyle.TEMPLATE
            p.required = true
            p.defaultValue = "." + defaultMediaTypeExtension
        }

        def params = extractUriParams( res.key, res.value.uriParmeters )
        params.putAll( baseUriParams )
        params.each {
            def p = addParamFromNamedProperty( resource.params, ParameterStyle.TEMPLATE, it )

            // workaround for bug in SoapUI 4.6.X
            if( p.style == ParameterStyle.TEMPLATE &&
                resource.service.basePath.contains( "{" + p.name + "}"))
            {
                resource.path = resource.path.replaceAll( "\\{" + p.name + "\\}", "")
            }
        }

        res.value.each {

            def key = it.key
            def optional = key.endsWith( '?')

            if( optional )
                key = key.substring( 0, key.length()-1 )

            if( Arrays.asList(RequestMethod.methodsAsStringArray).contains( key.toUpperCase() ))
            {
                def method = resource.getRestMethodByName( key )
                if( method == null && !optional )
                {
                    method = resource.addNewMethod( key )
                    method.method = RequestMethod.valueOf( key.toUpperCase() )
                }

                if( method != null )
                    initMethod( it, method, resourceTraits )
            }
        }

        if( res.value.type != null && resourceTypes.containsKey( res.value.type ))
            applyResourceType( resource, resourceTypes[res.value.type])
    }

    def applyResourceType( RestResource resource, def type )
    {
       type.each{
           def key = it.key
           def optional = key.endsWith( '?')

           if( optional )
               key = key.substring( 0, key.length()-1 )

           if( key.startsWith( '/'))
           {
               def name = getResourceName( it )
               def child = resource.getChildResourceByName( name )
               if( child == null && !optional )
               {
                   child = resource.addNewChildResource( name, key )
               }

               if( child != null )
                   initResource( child, it.value, null )
           }
           else if( Arrays.asList(RequestMethod.methodsAsStringArray).contains( key.toUpperCase() ))
           {
               def method = resource.getRestMethodByName( key )
               if( method == null && !optional )
               {
                   method = resource.addNewMethod( key )
                   method.method = RequestMethod.valueOf( key.toUpperCase() )
               }

               if( method != null )
                   initMethod( it, method, null )
           }

       }
    }

    public void initMethod(it, RestMethod method, resourceTraits) {

        if( it.value == "" )
            return

        if( method.description != null )
            method.description = it.value.description

        addMethodParameters(method, it.value)

        if (it.value.body != null)
            addBody(method, it.value.body)

        addResponses(method, it.value)
        applyTraits(it, method, resourceTraits)

        method.addNewRequest("Request 1")
    }

    def applyTraits(it, method, resourceTraits) {
        it.value.is?.each {
            if (traits[it] != null) {
                addMethodParameters(method, traits[it])
            }
        }

        resourceTraits?.each {
            if (traits[it] != null) {
                addMethodParameters(method, traits[it])
            }
        }
    }

    private addMethodParameters( def method, def raml )
    {
        raml.queryParameters?.each{
            addParamFromNamedProperty( method.params, ParameterStyle.QUERY, it )
        }

        raml.headers?.each{
            addParamFromNamedProperty( method.params, ParameterStyle.QUERY, it )
        }
    }

    private addBody( RestMethod method, def body )
    {
        body.each{
            def rep = method.addNewRepresentation( RestRepresentation.Type.REQUEST )
            rep.mediaType = it.key

            if( rep.mediaType.equals( "application/x-www-form-urlencoded") ||
                rep.mediaType.equals( "multipart/form-data"))
            {
                it.value.formParameters.each{
                    addParamFromNamedProperty( method.params, ParameterStyle.QUERY, it )
                }
            }
        }
    }

    private RestParameter addParamFromNamedProperty( def params, def style, def prop )
    {
        def key = prop.key
        def optional = key.endsWith( '?')

        if( optional )
            key = key.substring( 0, key.length()-1 )

        def param = params.getProperty( key )
        if( param == null )
        {
            if( optional )
                return

            param = params.addProperty( key )
        }

        param.style = style
        try {
            if( prop.value.description != null )
                param.description = prop.value.description

            if( prop.value.default != null )
                param.defaultValue = prop.value.default

            if( prop.value.required != null )
                param.required = Boolean.valueOf( prop.value.required )

            if( prop.value.enum != null )
                param.options = prop.value.enum

            if( param.type == null )
            {
                param.type = XmlString.type.name

                switch( prop.value.type ){
                    case "number" : param.type = XmlDouble.type.name; break;
                    case "integer" : param.type = XmlInteger.type.name; break;
                    case "date" : param.type = XmlDate.type.name; break;
                    case "boolean" : param.type = XmlBoolean.type.name; break;
                }
            }
        }
        catch (e )
        {
            Console.println( "Failed to init param " + prop )
        }

        return param
    }

    private addResponses( RestMethod method, def raml )
    {
        raml.responses?.each{

            def key = it.key
            def optional = key.endsWith( '?')
            if( optional )
                key = key.substring( 0, key.length()-1 )

            def representation = method.representations.find { r -> r.status.contains( Integer.parseInt( key ))}

            if( representation == null )
            {
                if( optional )
                    return;

                representation = method.addNewRepresentation(
                    Integer.parseInt(key) < 400 ? RestRepresentation.Type.RESPONSE : RestRepresentation.Type.FAULT )

                representation.status = [key]
            }

            if( representation.description == null )
                representation.description = it.value.description
        }
    }

	private RestService createRestService( def raml )
	{
        def path = raml.baseUri
        if( path.endsWith( "/"))
            path = path.substring( 0, path.length()-1 )

		URL url = new URL( path )
		def pathPos = path.length()-url.path.length()

		RestService restService = project.addNewInterface( raml.title, RestServiceFactory.REST_TYPE )
		restService.basePath = path.substring( pathPos )
		restService.addEndpoint( path.substring( 0, pathPos ))

		return restService
	}

    private Map extractUriParams( def path, def uriParameters )
    {
        def uriParams = [:]

        RestUtils.extractTemplateParams( path ).each {
            uriParams[it] = [:]
        }

        uriParameters?.each{
            uriParams[it.key] = it.value
        }

        uriParams.each{
           it.value.required = true
        }

        return uriParams
    }
}
