package com.smartbear.soapui.raml.actions;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.analytics.Analytics;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.RestResource;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.rest.mock.RestMockService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.registry.RestRequestStepFactory;
import com.eviware.soapui.support.UISupport;
import com.eviware.x.dialogs.Worker;
import com.eviware.x.dialogs.XProgressMonitor;
import com.eviware.x.form.XFormDialog;
import com.smartbear.soapui.raml.AbstractRamlImporter;
import com.smartbear.soapui.raml.RamlV08Importer;
import com.smartbear.soapui.raml.RamlV10Importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class RamlImporterWorker extends Worker.WorkerAdapter {
    private static final String RAML_V08_COMMENT = "#%RAML 0.8";
    private static final String RAML_V10_COMMENT = "#%RAML 1.0";
    private static final String RAML_VERSION_MESSAGE = "The service is described by using RAML v%s.";

    private final String finalExpUrl;
    private WsdlProject project;
    private XFormDialog dialog;

    public RamlImporterWorker(String finalExpUrl, WsdlProject project, XFormDialog dialog) {
        this.finalExpUrl = finalExpUrl;
        this.project = project;
        this.dialog = dialog;
    }

    public Object construct(XProgressMonitor monitor) {
        try {
            // create the importer and import!
            SoapUI.log("Importing RAML from [" + finalExpUrl + "]");
            AbstractRamlImporter importer = createRamlImporter(finalExpUrl);
            importer.setCreateSampleRequests(dialog.getBooleanValue(CreateRamlProjectAction.Form.CREATE_REQUESTS));
            SoapUI.log("CWD:" + new File(".").getCanonicalPath());
            RestMockService mockService = null;

            if (dialog.getBooleanValue(CreateRamlProjectAction.Form.GENERATE_MOCK)) {
                mockService = project.addNewRestMockService("Generated Virt");
                importer.setRestMockService(mockService);
            }

            RestService restService = importer.importRaml(finalExpUrl);

            if (mockService != null) {
                mockService.setName(restService.getName() + " Virt");
            }

            if (dialog.getBooleanValue(CreateRamlProjectAction.Form.GENERATE_TESTSUITE)) {
                WsdlTestSuite testSuite = project.addNewTestSuite("TestSuite");
                generateTestSuite(restService, testSuite);
            }

            UISupport.select(restService);
            Analytics.trackAction("ImportRAML");

            return restService;
        } catch (Throwable e) {
            UISupport.showErrorMessage(e);
        }

        return null;
    }

    public void generateTestSuite(RestService service, WsdlTestSuite testSuite) {
        for (RestResource resource : service.getAllResources()) {

            WsdlTestCase testCase = testSuite.addNewTestCase(resource.getName() + " TestCase");
            testCase.setDescription("TestCase generated for REST Resource [" + resource.getName() + "] located at ["
                    + resource.getFullPath(false) + "]");

            if (resource.getRequestCount() > 0) {
                for (int x = 0; x < resource.getRequestCount(); x++) {
                    RestRequest request = resource.getRequestAt(x);
                    testCase.addTestStep(RestRequestStepFactory.createConfig(request, request.getName()));
                }
            }
        }
    }

    private AbstractRamlImporter createRamlImporter(String url) throws Exception {
        InputStream inputStream = new URL(url).openStream();
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String ramlYamlComment = bufferedReader.readLine();
        bufferedReader.close();

        if (ramlYamlComment.equals(RAML_V08_COMMENT)) {
            SoapUI.log(String.format(RAML_VERSION_MESSAGE, "0.8"));
            return new RamlV08Importer(project);
        } else if (ramlYamlComment.equals(RAML_V10_COMMENT)) {
            SoapUI.log(String.format(RAML_VERSION_MESSAGE, "1.0"));
            return new RamlV10Importer(project);
        }

        throw new Exception("Unable to determine the RAML version.");
    }
}
