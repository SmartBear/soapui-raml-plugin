package com.smartbear.soapui.raml

import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.rest.mock.RestMockService
import com.eviware.soapui.impl.wsdl.WsdlProject

abstract class AbstractRamlImporter {
    protected final WsdlProject project
    protected RestMockService restMockService
    protected boolean createSampleRequests

    public AbstractRamlImporter(WsdlProject project) {
        this.project = project
    }

    public void setRestMockService(RestMockService restMockService) {
        this.restMockService = restMockService
    }

    public void setCreateSampleRequests(boolean createSampleRequests) {
        this.createSampleRequests = createSampleRequests;
    }

    public abstract RestService importRaml(String url)
}