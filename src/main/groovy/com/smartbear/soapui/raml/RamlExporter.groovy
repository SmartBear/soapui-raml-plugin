/**
 *  Copyright 2014 SmartBear Software, Inc.
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

import com.eviware.soapui.impl.rest.RestMethod
import com.eviware.soapui.impl.rest.RestRepresentation
import com.eviware.soapui.impl.rest.RestRequestInterface
import com.eviware.soapui.impl.rest.RestResource
import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.rest.support.RestParameter
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder
import com.eviware.soapui.impl.wsdl.WsdlProject
import com.eviware.soapui.support.StringUtils
import org.raml.emitter.RamlEmitter
import org.raml.model.Action
import org.raml.model.ActionType
import org.raml.model.MimeType
import org.raml.model.Raml
import org.raml.model.Resource
import org.raml.model.Response
import org.raml.model.parameter.AbstractParam
import org.raml.model.parameter.Header
import org.raml.model.parameter.QueryParameter
import org.raml.model.parameter.UriParameter

class RamlExporter {

    private final WsdlProject project
    private final String defaultMediaType
    private boolean createSampleBodies

    public RamlExporter( WsdlProject project, String defaultMediaType ) {
        this.project = project
        this.defaultMediaType = defaultMediaType
    }

    public RamlExporter( WsdlProject project ) {
        this(project, "application/json")
    }

    String createRaml(String title, RestService service, String baseUri, String version ) {

        Raml raml = new Raml()

        raml.title = title
        raml.baseUri = baseUri

        if( hasContent( version )) {
            raml.version = version

            if( baseUri.contains( "{version}"))
            {
                UriParameter uriParameter = new UriParameter();
                uriParameter.setDefaultValue( version );
                uriParameter.setRequired( true )
                raml.baseUriParameters.put( "version", uriParameter )
            }
        }

        exportRestService( service, raml )

        RamlEmitter emitter = new RamlEmitter()
        return emitter.dump( raml )
    }

    def exportRestService(RestService restService, Raml raml) {
        restService.resourceList.each {
            def Resource res = createRamlResource( it, raml )
            raml.resources.put( ensureRelativePath(it.path), res )
        }
    }

    def ensureRelativePath( String path )
    {
        if( !path.startsWith("/"))
            path = "/" + path

        return path
    }

    def createRamlResource( RestResource resource, Raml raml )
    {
        Resource result = new Resource()
        result.displayName = resource.name

        if( hasContent(resource.description))
            result.description = resource.description

        resource.params.values().each {
            RestParameter p = it

            if( p.style == RestParamsPropertyHolder.ParameterStyle.TEMPLATE ) {
                if( !p.name.equals( "version") || !raml.baseUriParameters.containsKey("version"))
                    result.baseUriParameters.put(p.name, createUriParameter(p))
            }
        }

        resource.restMethodList.each {
            Action action = createRamlAction( it, raml )
            result.actions.put( ActionType.valueOf( it.method.name().toUpperCase()), action )
        }

        resource.childResourceList.each {
            Resource res = createRamlResource( it, raml )
            result.resources.put( ensureRelativePath(it.path), res )
        }

        return result
    }

    def createRamlAction( RestMethod restMethod, Raml raml )
    {
        Action result = new Action()

        restMethod.params.values().each {
            RestParameter p = it

            if( hasContent( p.name ))
            {
                switch (p.style) {
                    case RestParamsPropertyHolder.ParameterStyle.QUERY:
                        result.queryParameters.put(p.name, createQueryParameter(p))
                        break;
                    case RestParamsPropertyHolder.ParameterStyle.HEADER:
                        result.headers.put(p.name, createHeaderParameter(p))
                        break;
                    case RestParamsPropertyHolder.ParameterStyle.TEMPLATE:
                        if( !p.name.equals( "version") || !raml.baseUriParameters.containsKey("version"))
                            result.baseUriParameters.put(p.name, createUriParameter(p))

                        break;
                }
            }
        }

        // add parameters defined in parent resources (since raml doesn't have query/header parameters at the resource level)
        restMethod.overlayParams.values().each {
            RestParameter p = it

            if( hasContent( p.name )) {
                switch (p.style) {
                    case RestParamsPropertyHolder.ParameterStyle.QUERY:
                        if (!result.queryParameters.containsKey(p.name))
                            result.queryParameters.put(p.name, createQueryParameter(p))
                        break;
                    case RestParamsPropertyHolder.ParameterStyle.HEADER:
                        if (!result.headers.containsKey(p.name))
                            result.headers.put(p.name, createHeaderParameter(p))
                        break;
                }
            }
        }

        result.body = new HashMap<String,MimeType>()

        restMethod.representations.each {

            def mediaType = hasContent(it.mediaType) ? it.mediaType : defaultMediaType

            if( it.type == RestRepresentation.Type.REQUEST && (
                restMethod.method == RestRequestInterface.HttpMethod.POST ||
                restMethod.method == RestRequestInterface.HttpMethod.PUT ))
            {
                if( hasContent(mediaType)) {
                    result.body.put(mediaType, new MimeType())

                    if( createSampleBodies )
                    {
                        restMethod.requestList.each {
                            def mimeType = result.body.get( mediaType )
                            if( mimeType != null && !hasContent(mimeType.example) && hasContent( it.requestContent ))
                            {
                                mimeType.example = it.requestContent
                            }
                        }
                    }
                }
            }
            else if( it.type == RestRepresentation.Type.RESPONSE || it.type == RestRepresentation.Type.FAULT )
            {
                if( hasContent( mediaType )) {
                    Response response = new Response()
                    response.body = new HashMap<String, MimeType>()
                    response.body.put(mediaType, new MimeType())

                    it.status.each {
                        int statusCode = it
                        result.responses.put(String.valueOf(statusCode), response)

                        if( createSampleBodies )
                        {
                            restMethod.requestList.each {
                                if( it.response != null && it.response.statusCode == statusCode) {

                                    def mimeType = response.body.get(it.response.contentType )
                                    if (mimeType != null && !hasContent(mimeType.example) && hasContent(it.response.contentAsString)) {
                                        mimeType.example = it.response.contentAsString
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    def createQueryParameter( RestParameter p )
    {
        return setDefaultParameterProperties(p, new QueryParameter())
    }

    public def setDefaultParameterProperties(RestParameter p, AbstractParam result) {
        result.required = p.required

        if (hasContent(p.defaultValue))
            result.defaultValue = p.defaultValue

        if (hasContent(p.description))
            result.description = p.description

        return result
    }

    def createHeaderParameter( RestParameter p )
    {
        return setDefaultParameterProperties( p, new Header() )
    }

    def createUriParameter( RestParameter p )
    {
        UriParameter result = setDefaultParameterProperties(p, new UriParameter())

        List<UriParameter> list = new ArrayList<UriParameter>();
        list.add( result )
        return list
    }

    public static String createFileName( String path, String title )
    {
        return path + File.separatorChar + StringUtils.createFileName( title, (char)'-' ) + ".raml"
    }

    static def hasContent( String str )
    {
        return str != null && str.trim().length() > 0
    }

    public def setCreateSampleBodies( boolean createSampleBodies )
    {
        this.createSampleBodies = createSampleBodies
    }
}
