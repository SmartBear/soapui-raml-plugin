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

import com.eviware.soapui.impl.rest.*
import com.eviware.soapui.impl.rest.mock.RestMockService
import com.eviware.soapui.impl.rest.support.RestParameter
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder.ParameterStyle
import com.eviware.soapui.impl.rest.support.RestUtils
import com.eviware.soapui.impl.wsdl.WsdlProject
import org.apache.xmlbeans.*
import org.raml.model.Action
import org.raml.model.MimeType
import org.raml.model.ParamType
import org.raml.model.Raml
import org.raml.model.Resource
import org.raml.model.Response
import org.raml.model.parameter.AbstractParam
import org.raml.model.parameter.UriParameter
import org.raml.parser.loader.DefaultResourceLoader
import org.raml.parser.loader.FileResourceLoader
import org.raml.parser.tagresolver.ContextPath
import org.raml.parser.visitor.RamlDocumentBuilder

/**
 * A simple RAML importer that uses the raml-java-parser
 *
 * @author Ole Lensmar
 */

class RamlV08Importer {

    private static final String MEDIA_TYPE_EXTENSION = "{mediaTypeExtension}"
    private final WsdlProject project
    private String defaultMediaType
    private String defaultMediaTypeExtension
    private def baseUriParams = [:]
    private RestMockService restMockService
    private boolean createSampleRequests

    public RamlV08Importer(WsdlProject project) {
        this.project = project
    }

    public void setRestMockService( RestMockService restMockService )
    {
        this.restMockService = restMockService
    }

    public RestService importRaml(String url) {


        Raml raml;

        def builder = new RamlDocumentBuilder()
        if( url.toLowerCase().startsWith("file:"))
        {
            def parent = new File( url.substring(5)).getParentFile().toURI().toURL().toString()
            raml = builder.build(new URL(url).openStream(), parent );
        }
        else {
            raml = builder.build(url);
        }

        def service = createRestService(raml)

        baseUriParams = extractUriParams(raml.baseUri, raml.baseUriParameters)
        if (baseUriParams.version != null)
            baseUriParams.version.defaultValue = raml.version

        // extract default media type
        if (raml.mediaType != null) {
            defaultMediaType = raml.mediaType
            defaultMediaTypeExtension = defaultMediaType
            if (defaultMediaTypeExtension.contains('/'))
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring(defaultMediaTypeExtension.lastIndexOf('/') + 1)
            if (defaultMediaTypeExtension.contains('-'))
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring(defaultMediaTypeExtension.lastIndexOf('-') + 1)
        }

        raml.resources.each {
            addResource(service, it.key, it.value)
        }

        return service
    }

    def addResource(RestService service, String path, Resource r ) {

        def resource = service.addNewResource(getResourceName(r), path)
        initResource(resource, r)

        r.resources.each {
           addChildResource(resource, it.key, it.value)
        }
    }

    String getResourceName(Resource r) {
        String name = r.displayName

        if (name == null)
            name = r.relativeUri

        if (name.endsWith(MEDIA_TYPE_EXTENSION))
            name = name.substring(0, name.length() - 20)

        return name
    }

    def addChildResource(RestResource resource, String path, Resource r) {
        def childResource = resource.addNewChildResource(getResourceName(r), path)

        initResource(childResource, r)

        if (baseUriParams.version != null)
            childResource.params.removeProperty("version")


        r.resources.each {
            addChildResource(childResource, it.key, it.value)
        }
    }

    def initResource(RestResource resource, Resource r ) {

        resource.description = r.description

        if (r.uri.contains(MEDIA_TYPE_EXTENSION)) {
            RestParameter p = resource.params.addProperty("mediaTypeExtension")
            p.style = ParameterStyle.TEMPLATE
            p.required = true
            p.defaultValue = "." + defaultMediaTypeExtension
        }

        def params = extractUriParams(r.uri, r.uriParameters )
        params.putAll(baseUriParams)
        params.each {
            addParamFromNamedProperty(resource.params, ParameterStyle.TEMPLATE, it.key, it.value )
        }

        // workaround for bug in SoapUI that adds template parameters to path
        baseUriParams.each {
            resource.path = resource.path.replaceAll( "\\{" + it.key + "\\}", "");
        }

        r.actions.each {

            def key = it.key.toString()
            if (Arrays.asList(RestRequestInterface.HttpMethod.methodsAsStringArray).contains(key)) {
                def method = resource.getRestMethodByName(key)
                if (method == null ) {
                    method = resource.addNewMethod(key.toLowerCase())
                    method.method = RestRequestInterface.HttpMethod.valueOf(key.toUpperCase())
                }

                initMethod(method, it.value )
            }
        }
    }

    public void initMethod( RestMethod method, Action action ) {

        method.description = action.description

        action.queryParameters?.each {
            addParamFromNamedProperty(method.params, ParameterStyle.QUERY, it.key, it.value)
        }

        action.headers?.each {
            addParamFromNamedProperty(method.params, ParameterStyle.HEADER, it.key, it.value)
        }

        if( action.body != null )
            addRequestBody(method, action.body)

        if( action.responses != null)
            addResponses(method, action.responses )

        if (method.requestCount == 0 && createSampleRequests )
        {
            initDefaultRequest( method.addNewRequest("Request 1"))
        }
    }

    RestRequest initDefaultRequest(RestRequest request)
    {
        if( defaultMediaType != null )
        {
            def headers = request.requestHeaders
            headers.Accept = [defaultMediaType]
            request.requestHeaders = headers
        }

        return request
    }

    private addRequestBody(RestMethod method, Map body) {
        body.each {
            def rep = method.addNewRepresentation(RestRepresentation.Type.REQUEST)
            MimeType mt = it.value
            rep.mediaType = mt.type

            if (rep.mediaType.equals("application/x-www-form-urlencoded") ||
                    rep.mediaType.equals("multipart/form-data")) {
                mt.formParameters?.each {
                    def name = it.key
                    if( it.value.size() > 0 )
                        addParamFromNamedProperty(method.params, ParameterStyle.QUERY, name, it.value.get(0))
                }
            }

            rep.sampleContent = mt.example

            if (mt.example != null && createSampleRequests ) {
                def request = initDefaultRequest( method.addNewRequest("Sample Request"))
                request.mediaType = mt.type
                request.requestContent = mt.example
            }
        }
    }

    private RestParameter addParamFromNamedProperty(def params, def style, def name, AbstractParam p ) {

        RestParameter param = params.getProperty( name )
        if( param == null )
           param = params.addProperty(name)

        param.style = style

        if( param.description == null || param.description == "" )
            param.description = p.description

        if( param.defaultValue == null || param.defaultValue == "")
            param.defaultValue = p.defaultValue

        param.required = p.required

        if( param.options == null || param.options.length == 0 )
            param.options = p.enumeration

        if( param.type == null )
            param.type = XmlString.type.name

        switch (p.type) {
            case ParamType.NUMBER: param.type = XmlDouble.type.name; break;
            case ParamType.INTEGER: param.type = XmlInteger.type.name; break;
            case ParamType.DATE: param.type = XmlDate.type.name; break;
            case ParamType.BOOLEAN: param.type = XmlBoolean.type.name; break;
        }

        return param
    }

    private addResponses(RestMethod method, Map responses) {
        responses?.each {

            int statusCode = Integer.parseInt(it.key)
            Response r = it.value

            if (r.body == null || r.body.isEmpty()) {
                def representation = method.representations.find {
                    it.status.contains(statusCode)
                }

                if (representation == null) {
                    representation = method.addNewRepresentation(
                            statusCode < 400 ? RestRepresentation.Type.RESPONSE : RestRepresentation.Type.FAULT)

                    representation.status = [statusCode]
                } else if (!representation.status.contains(statusCode)) {
                    representation.status = representation.status + statusCode
                }

                representation.description = r.description

            } else r.body?.each {
                MimeType mt = it.value

                def representation = method.representations.find {
                    it.status.contains(statusCode) && (mt.type == null || mt.type.equals(it.mediaType))
                }

                if (representation == null) {
                    representation = method.addNewRepresentation(
                            statusCode < 400 ? RestRepresentation.Type.RESPONSE : RestRepresentation.Type.FAULT)

                    representation.status = [statusCode]
                    representation.mediaType = mt.type
                } else if (!representation.status.contains(statusCode)) {
                    representation.status = representation.status + statusCode
                }

                representation.description = r.description
                representation.sampleContent = mt.example
            }
        }

        if (restMockService != null) {
            def path = method.resource.getFullPath(true)
            def params = method.overlayParams

            params.each {
                RestParameter p = it.value
                if( p.style == ParameterStyle.TEMPLATE )
                {
                    if( p.defaultValue != null && p.defaultValue.trim().length() > 0 )
                        path = path.replaceAll( "\\{" + it.key + "\\}", p.defaultValue )
                    else
                        path = path.replaceAll( "\\{" + it.key + "\\}", it.key )
                }
            }

            def mockAction = restMockService.addEmptyMockAction(method.method, path)

            responses?.each {

                int statusCode = Integer.parseInt(it.key)
                Response r = it.value

                r.body?.each {
                    def mockResponse = mockAction.addNewMockResponse("Response " + statusCode)
                    mockResponse.setContentType( it.value.type )

                    if( it.value.example != null )
                        mockResponse.responseContent = String.valueOf( it.value.example )
                }
            }

        }
    }


    private RestService createRestService(def raml) {
        RestService restService = project.addNewInterface(raml.title, RestServiceFactory.REST_TYPE)

        def path = raml.baseUri
        if (path != null) {
            if (path.endsWith("/"))
                path = path.substring(0, path.length() - 1)

            URL url = new URL(path)
            def pathPos = path.length() - url.path.length()

            restService.basePath = path.substring(pathPos)
            restService.addEndpoint(path.substring(0, pathPos))
        }

        return restService
    }

    private Map extractUriParams(def path, def uriParameters) {
        def uriParams = [:]

        RestUtils.extractTemplateParams(path).each {
            def p = new UriParameter()
            p.displayName = it
            uriParams[it] = p
        }

        uriParameters?.each {
            uriParams[it.key] = it.value
        }

        uriParams.each {
            it.value.required = true
        }

        return uriParams
    }

    public void setCreateSampleRequests ( boolean createSampleRequests )
    {
        this.createSampleRequests = createSampleRequests;
    }
}
