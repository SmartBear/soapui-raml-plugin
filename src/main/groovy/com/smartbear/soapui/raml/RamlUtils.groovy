package com.smartbear.soapui.raml

import com.eviware.soapui.SoapUI
import com.eviware.soapui.impl.wsdl.WsdlProject

class RamlUtils {
    private static final String RAML_V08_COMMENT = "#%RAML 0.8"
    private static final String RAML_V10_COMMENT = "#%RAML 1.0"
    private static final String RAML_VERSION_MESSAGE = "The service is described by using RAML v%s."

    public static AbstractRamlImporter createRamlImporter(String url, WsdlProject project) {
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
