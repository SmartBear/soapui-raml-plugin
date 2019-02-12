## soapui-raml-plugin

- Allows you to import RAML files into SoapUI for testing your REST APIs
- Allows you to generate a REST Mock Service for a RAML file being imported
- Allows you to update an existing REST Service in SoapUI from a RAML file
- Allows you to generate a RAML file for any REST API defined in SoapUI


-> See the [blog-post](http://olensmar.blogspot.se/2013/12/a-raml-apihub-plugin-for-soapui.html) for a detailed overview.

### Download & Install

As of the 1.2 version the plugin is only available via the Plugin Repository / Plugin Manager button in top 
toolbars of either SoapUI Pro 5.1+ or ReadyAPI 1.0+

### Build it yourself

Clone the Git repository, make sure you have maven installed, and run

```
mvn clean install assembly:single
```
### Features and shortcomings

The RAML importer supports most constructs in the [RAML 0.8 specification](http://raml.org/spec.html), including
parameterized traits and resourceTypes, !include statements, child resources, query/header/form/uri parameters,
example requests bodies, etc.

Currently ignored are:
- security declarations - mainly because SoapUI doesn't support OAuth yet
- protocols - not sure how that would be mapped to SoapUI where you can add as many endpoints as needed
- schemas - the SoapUI API doesn't make them easy to add programmatically, and it only supports XML Schema for now

I've tested this with a number of RAML files (see src/test/resources and the RamlImporterTests),
but I'm sure there are details I've missed - please let me know if you find anything strange or unexpected.

### Release History
- Version 1.3.1 - June 2015
  - Updated raml-parser dependency for latest fixes
  - Removed “Update from RAML definition” option (since REST refactoring is now in R!A 1.3)
  - Removed the functionality related to APIHub (due to APIHub retirement)
- Version 1.3
  - added support for ApiImporter Plugin Interface and saving of example requests/responses to RestRepresentaiton sampleContent
- Version 1.2 - 2014-11-21 
  - embedded updated swagger plugin to avoid runtime dependencey
  - embedded 3rd party libraries so only one distributable file is needed
  - improved error handling and messages
- Version 0.4 - 2014-05-13
  - Added initial "Export RAML" functionality to REST Service popup menu
  - Fixed import of RAML files containing relative includes and multiple request body examples.
  - Added an option do the Import and Update dialogs to enable/disable creation of sample requests.
  - Added separators to popup menus for better readability
- Version 0.3 - 2014-04-22
  - Bugfixes and new "Update from RAML Definition" action which adds new resources/methods/parameters to an existing REST Service in SoapUI
  from the specified RAML file.
  - Added "Generate Mock Service" option when Importing RAML definitions - which creates a SoapUI 5.0 REST Mock using sample response bodies if available.
- Version 0.2 - 2013-11-27
  - Update to use [raml-java-parser](https://github.com/raml-org/raml-java-parser) instead of own raml parser
- Version 0.1 - 2013-11-22 - Initial release

### Feedback is welcome!

Please don't hesitate to comment here or get in touch on twitter; @olensmar

Thanks!

/Ole
