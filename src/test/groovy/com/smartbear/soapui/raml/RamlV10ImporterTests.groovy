package com.smartbear.soapui.raml

import com.eviware.soapui.impl.rest.RestRequestInterface
import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder
import com.eviware.soapui.impl.wsdl.WsdlProject

class RamlV10ImporterTests extends GroovyTestCase {
    public static def importRaml(def path) {
        WsdlProject project = new WsdlProject()
        RamlV10Importer importer = new RamlV10Importer(project)
        importer.setRestMockService(project.addNewRestMockService("TestRESTMock"))
        importer.setCreateSampleRequests(true)

        String uri = path.startsWith('http') ? path : new File("src/test/resources/ramlv10/" + path).toURI().toURL().toString();
        return importer.importRaml(uri);
    }

    public void testAddhereTech() {
        RestService restService = importRaml("adheretech/api.raml")

        assertEquals("AdhereTech External API", restService.name)
        assertEquals("An API designed to expose AdhereTech server's data to clients and other services.", restService.description)
        assertEquals("http://adheretech.com", restService.endpoints[0])
        assertEquals("/api/{version}", restService.basePath)

        def resource = restService.resources["/bottles"]
        assertNotNull(resource)
        assertTrue(resource.params.hasProperty("version"))
        assertEquals(RestParamsPropertyHolder.ParameterStyle.TEMPLATE, resource.getParams().getProperty("version").style)
        assertEquals("v1", resource.params.version.defaultValue)

        resource = resource.getChildResourceByName("/{bottleUid}")
        assertNotNull(resource)
        //version should only be defined on root resources
        assertFalse(resource.params.hasProperty("version"))
        assertTrue(resource.params.hasProperty("bottleUid"))
        assertEquals(RestParamsPropertyHolder.ParameterStyle.TEMPLATE, resource.getParams().getProperty("bottleUid").style)

        def method = resource.getRestMethodByName("get")
        assertNotNull(method)
        assertEquals(RestRequestInterface.HttpMethod.GET, method.method)

        assertTrue(method.representations[0].status.contains(200))
        assertEquals("application/json", method.representations[0].mediaType)
        assertTrue(method.representations[1].status.contains(404))
    }
}
