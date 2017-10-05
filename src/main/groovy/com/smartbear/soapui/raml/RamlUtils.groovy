package com.smartbear.soapui.raml

import com.eviware.soapui.SoapUI
import com.eviware.soapui.impl.wsdl.WsdlProject
import org.apache.log4j.Logger

import java.lang.reflect.Method

class RamlUtils {
    private static final String RAML_V08_COMMENT = "#%RAML 0.8"
    private static final String RAML_V10_COMMENT = "#%RAML 1.0"
    private static final String RAML_VERSION_MESSAGE = "The service is described by using RAML v%s."
    private static final Logger logger = org.apache.log4j.Logger.getLogger(RamlUtils.class)

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

        throw new Exception("Unable to determine the RAML version. The first line of a RAML API definition document must begin with the text #%RAML 0.8 or #%RAML 1.0");
    }

    public static void sendAnalytics(String action) {
        Class analyticsClass;
        try {
            analyticsClass = Class.forName("com.smartbear.analytics.Analytics");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Method getManagerMethod = analyticsClass.getMethod("getAnalyticsManager");
            Object analyticsManager = getManagerMethod.invoke(null);
            Class analyticsCategoryClass = Class.forName('com.smartbear.analytics.AnalyticsManager$Category');
            Method trackMethod = analyticsManager.getClass().getMethod("trackAction", analyticsCategoryClass,
                    String.class, Map.class);
            Map<String, String> params = new HashMap();
            params.put("SourceModule", "");
            params.put("ProductArea", "MainMenu");
            params.put("Type", "REST");
            params.put("Source", "RamlDefinition");
            trackMethod.invoke(analyticsManager, Enum.valueOf(analyticsCategoryClass, "CUSTOM_PLUGIN_ACTION"),
                    action, params);
        } catch (Throwable e) {
            logger.error("Error while sending analytics", e);
        }
    }
}
