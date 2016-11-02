package com.smartbear.soapui.raml

import com.eviware.soapui.SoapUI
import com.eviware.soapui.impl.rest.RestMethod
import com.eviware.soapui.impl.rest.RestRepresentation
import com.eviware.soapui.impl.rest.RestRequest
import com.eviware.soapui.impl.rest.RestRequestInterface
import com.eviware.soapui.impl.rest.RestResource
import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.rest.RestServiceFactory
import com.eviware.soapui.impl.rest.mock.RestMockService
import com.eviware.soapui.impl.rest.support.RestParameter
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder.ParameterStyle
import com.eviware.soapui.impl.rest.support.RestUtils
import com.eviware.soapui.impl.wsdl.WsdlProject
import com.smartbear.ready.core.ReadyApiCoreModule
import org.apache.xmlbeans.XmlBoolean
import org.apache.xmlbeans.XmlDate
import org.apache.xmlbeans.XmlDouble
import org.apache.xmlbeans.XmlInteger
import org.apache.xmlbeans.XmlString
import org.raml.v2.api.RamlModelBuilder
import org.raml.v2.api.RamlModelResult
import org.raml.v2.api.model.v10.bodies.Response
import org.raml.v2.api.model.v10.methods.Method
import org.raml.v2.api.model.v10.api.Api
import org.raml.v2.api.model.v10.bodies.MimeType
import org.raml.v2.api.model.v10.datamodel.IntegerTypeDeclaration
import org.raml.v2.api.model.v10.datamodel.NumberTypeDeclaration
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration
import org.raml.v2.api.model.v10.resources.Resource
import org.raml.v2.internal.impl.v10.grammar.BuiltInScalarType

class RamlV10Importer {
    private static final String MEDIA_TYPE_EXTENSION = "{ext}"
    private final WsdlProject project
    private List<String> defaultMediaTypes
    private String defaultMediaTypeExtension
    private def baseUriParams = [:]
    private RestMockService restMockService
    private boolean createSampleRequests

    public RamlV10Importer(WsdlProject project) {
        this.project = project
    }

    public void setRestMockService(RestMockService restMockService) {
        this.restMockService = restMockService
    }

    public RestService importRaml(String url) {
        RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(url)
        if(ramlModelResult.hasErrors()) {
            ramlModelResult.getValidationResults().each {
                SoapUI.log.error(it.getMessage())
            }

            return null
        }

        return importRaml(ramlModelResult.apiV10)
    }

    public RestService importRaml(Api api) {

        def restService = createRestService(api)

        baseUriParams = extractUriParams(api.baseUri(), api.baseUriParameters())
        if (baseUriParams.version != null) {
            baseUriParams.version.defaultValue = api.version()
        }

        // extract default media type
        List<MimeType> mediaType = api.mediaType()
        if ((mediaType != null) && (!mediaType.isEmpty())) {
            defaultMediaTypes = new ArrayList<>()
            mediaType.each {
                defaultMediaTypes.add(it.value())
            }
            defaultMediaTypeExtension = defaultMediaTypes.get(0)
            if (defaultMediaTypeExtension.contains('/')) {
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring(defaultMediaTypeExtension.lastIndexOf('/') + 1)
            }
            if (defaultMediaTypeExtension.contains('-')) {
                defaultMediaTypeExtension = defaultMediaTypeExtension.substring(defaultMediaTypeExtension.lastIndexOf('-') + 1)
            }
        }

        api.resources().each {
            addResource(restService, it.relativeUri().value(), it)
        }

        return restService
    }

    def addResource(RestService restService, String path, Resource resource) {

        def restResource = restService.addNewResource(getResourceName(resource), path)
        initResource(restResource, resource)

        resource.resources().each {
            addChildResource(restResource, it.relativeUri().value(), it)
        }
    }

    String getResourceName(Resource resource) {
        String name = resource.displayName()

        if (name == null) {
            name = resource.relativeUri().value()
        }

        if (name.endsWith(MEDIA_TYPE_EXTENSION)) {
            name = name.substring(0, name.length() - MEDIA_TYPE_EXTENSION.length())
        }

        return name
    }

    def addChildResource(RestResource restResource, String path, Resource resource) {
        def childResource = restResource.addNewChildResource(getResourceName(resource), path)

        initResource(childResource, resource)

        if (baseUriParams.version != null) {
            childResource.params.removeProperty("version")
        }

        resource.resources().each {
            addChildResource(childResource, it.relativeUri().value(), it)
        }
    }

    def initResource(RestResource restResource, Resource resource) {

        restResource.description = resource.description()
        if (resource.relativeUri().value().contains(MEDIA_TYPE_EXTENSION)) {
            RestParameter extParameter = restResource.params.addProperty("ext")
            extParameter.style = ParameterStyle.TEMPLATE
            extParameter.required = true
            extParameter.defaultValue = "." + defaultMediaTypeExtension
        }

        def params = extractUriParams(resource.relativeUri(), resource.uriParameters())
        params.putAll(baseUriParams)
        params.each {
            addParamFromNamedProperty(restResource.params, ParameterStyle.TEMPLATE, it.value)
        }

        // workaround for bug in SoapUI that adds template parameters to path
        baseUriParams.each {
            restResource.path = restResource.path.replaceAll("\\{" + it.key + "\\}", "");
        }

        resource.methods()?.each {
            def key = it.method()
            if (Arrays.asList(RestRequestInterface.HttpMethod.methodsAsStringArray).contains(key)) {
                def restMethod = restResource.getRestMethodByName(key)
                if (restMethod == null) {
                    restMethod = restResource.addNewMethod(key.toLowerCase())
                    restMethod.method = RestRequestInterface.HttpMethod.valueOf(key.toUpperCase())
                }

                initMethod(restMethod, it)
            }
        }
    }

    public void initMethod(RestMethod restMethod, Method method) {
        restMethod.description = method.description().value()

        method.queryParameters()?.each {
            addParamFromNamedProperty(restMethod.params, ParameterStyle.QUERY, new RamlParameter(it))
        }

        method.headers()?.each {
            addParamFromNamedProperty(restMethod.params, ParameterStyle.HEADER, new RamlParameter(it))
        }

        if (method.body() != null) {
            addRequestBody(restMethod, method.body())
        }

        if (method.responses() != null) {
            addResponses(restMethod, method.responses())
        }

        if (restMethod.requestCount == 0 && createSampleRequests) {
            initDefaultRequest(restMethod.addNewRequest("Request 1"))
        }
    }

    RestRequest initDefaultRequest(RestRequest restRequest) {
        if (defaultMediaTypes != null) {
            def headers = restRequest.requestHeaders
            headers.Accept = defaultMediaTypes.toArray()
            restRequest.requestHeaders = headers
        }

        return restRequest
    }

    private addRequestBody(RestMethod restMethod, List<TypeDeclaration> body) {
        body.each {
            def representation = restMethod.addNewRepresentation(RestRepresentation.Type.REQUEST)
            representation.mediaType = it.name()

            /*if (representation.mediaType.equals("application/x-www-form-urlencoded") ||
                    representation.mediaType.equals("multipart/form-data")) {
                it.formParameters()?.each {
                    def name = it.key
                    if (it.value.size() > 0)
                        addParamFromNamedProperty(restMethod.params, ParameterStyle.QUERY, name, it.value.get(0))
                }
            }*/

            representation.sampleContent = it.example()?.value()

            if (it.example() != null && createSampleRequests) {
                def request = initDefaultRequest(restMethod.addNewRequest("Sample Request"))
                request.mediaType = it.name()
                request.requestContent = it.example().value()
            }
        }
    }

    private RestParameter addParamFromNamedProperty(def params, def style, RamlParameter uriParameter) {

        RestParameter param = params.getProperty(uriParameter.name())
        if (param == null) {
            param = params.addProperty(uriParameter.name())
        }

        param.style = style

        if (param.description == null || param.description == "") {
            param.description = uriParameter.description
        }

        if (param.defaultValue == null || param.defaultValue == "") {
            param.defaultValue = uriParameter.defaultValue
        }

        param.required = uriParameter.required()

        if (param.options == null || param.options.length == 0) {
            if (uriParameter instanceof StringTypeDeclaration) {
                param.options = ((StringTypeDeclaration) uriParameter).enumValues()
            } else if (uriParameter instanceof NumberTypeDeclaration) {
                param.options = ((NumberTypeDeclaration) uriParameter).enumValues()
            }
        }

        if (param.type == null) {
            param.type = XmlString.type.name
        }

        if (uriParameter.type().equals(BuiltInScalarType.NUMBER.getType())) {
            param.type = XmlDouble.type.name
        } else if (uriParameter.type().equals(BuiltInScalarType.INTEGER.getType())) {
            param.type = XmlInteger.type.name
        } else if (uriParameter.type().equals(BuiltInScalarType.BOOLEAN.getType())) {
            param.type = XmlBoolean.type.name
        } else if (isDateTimeType(uriParameter.type)) {
            param.type = XmlDate.type.name
        }

        return param
    }

    private boolean isDateTimeType(String type) {
        return type.equals(BuiltInScalarType.DATE_ONLY.getType()) || type.equals(BuiltInScalarType.DATE_TIME_ONLY.getType()) ||
                type.equals(BuiltInScalarType.TIME_ONLY.getType()) || type.equals(BuiltInScalarType.DATE_TIME.getType())
    }

    private addResponses(RestMethod method, List<Response> responses) {
        responses?.each {

            int statusCode = Integer.parseInt(it.code().value())

            if (it.body() == null || it.body().isEmpty()) {
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

                representation.description = it.description().value()

            } else it.body()?.each {
                TypeDeclaration declaration = it
                def representation = method.representations.find {
                    it.status.contains(statusCode) && (declaration.name() == null || declaration.name().equals(it.mediaType))
                }

                if (representation == null) {
                    representation = method.addNewRepresentation(
                            statusCode < 400 ? RestRepresentation.Type.RESPONSE : RestRepresentation.Type.FAULT)

                    representation.status = [statusCode]
                    representation.mediaType = declaration.name()
                } else if (!representation.status.contains(statusCode)) {
                    representation.status = representation.status + statusCode
                }

                representation.description = it.description()
                representation.sampleContent = it.example().value()
            }
        }

        if (restMockService != null) {
            def path = method.resource.getFullPath(true)
            def params = method.overlayParams

            params.each {
                RestParameter p = it.value
                if (p.style == ParameterStyle.TEMPLATE) {
                    if (p.defaultValue != null && p.defaultValue.trim().length() > 0) {
                        path = path.replaceAll("\\{" + it.key + "\\}", p.defaultValue)
                    } else {
                        path = path.replaceAll("\\{" + it.key + "\\}", it.key)
                    }
                }
            }

            def mockAction = restMockService.addEmptyMockAction(method.method, path)

            responses?.each {

                int statusCode = Integer.parseInt(it.code().value())
                it.body()?.each {
                    def mockResponse = mockAction.addNewMockResponse("Response " + statusCode)
                    mockResponse.setContentType(it.name())

                    if (it.example() != null) {
                        mockResponse.responseContent = String.valueOf(it.example().value())
                    }
                }
            }

        }
    }

    private RestService createRestService(Api api) {
        RestService restService = project.addNewInterface(api.title(), RestServiceFactory.REST_TYPE)

        def path = api.baseUri()
        if (path != null) {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1)
            }

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
            def parameter = new RamlParameter(it)
            uriParams[it] = parameter
        }

        uriParameters?.each {
            uriParams[it.name()] = new RamlParameter(it)
        }

        uriParams.each {
            it.value.required = true
        }

        return uriParams
    }

    public void setCreateSampleRequests(boolean createSampleRequests) {
        this.createSampleRequests = createSampleRequests;
    }

    private class RamlParameter {
        private final TypeDeclaration parameter
        private String name
        private String description
        private final String type
        private String defaultValue
        private boolean required

        public RamlParameter(TypeDeclaration parameter) {
            this.parameter = parameter
            type = parameter != null? parameter.type() : BuiltInScalarType.STRING.getType();
            name = parameter?.name()
            description = parameter?.description().value()
            defaultValue = parameter?.defaultValue()
            required = parameter?.required()
        }

        public RamlParameter(String name) {
            this(null)
            this.name = name
        }

        String getDescription() {
            return description
        }

        void setDescription(String description) {
            this.description = description
        }

        String getType() {
            return type
        }

        String getDefaultValue() {
            return defaultValue
        }

        void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue
        }


        String getName() {
            return name
        }

        void setName(String name) {
            this.name = name
        }

        boolean getRequired() {
            return required
        }

        void setRequired(boolean required) {
            this.required = required
        }

        List<String> enumValues() {
            if (parameter == null) {
                return  null
            }

            if(parameter instanceof StringTypeDeclaration) {
                return ((StringTypeDeclaration) parameter).enumValues()
            } else if((parameter instanceof NumberTypeDeclaration) || (parameter instanceof IntegerTypeDeclaration)) {
                return ((NumberTypeDeclaration) parameter).enumValues()
            }

            return null
        }
    }
}
