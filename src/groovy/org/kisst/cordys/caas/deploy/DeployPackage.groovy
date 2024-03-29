package org.kisst.cordys.caas.deploy

import org.kisst.cordys.caas.Caas
import org.kisst.cordys.caas.CordysSystem
import org.kisst.cordys.caas.EPackageStatus
import org.kisst.cordys.caas.helper.CAPPackage
import org.kisst.cordys.caas.helper.Log

/**
 * This script will upload an deploy the given package to the given Cordys environment using CAAS. This is the script to be used
 * whenever a package needs to be deployed.
 * 
 * This script has been wrapped in the deploy_package.cmd to make the calling of this script easier.
 * 
 * Normal usage from i.e. Jenkins is this commandline: 
 * deploy_package.cmd -f <folder> -s <targetsystem>
 * 
 * The options that can be passed on are:
 * -h:  Just displays the help information
 * -c:  The CAP file that should be deployed. You can use this option if you want to deploy a single CAP file.
 * -f:  The folder in which the CAP files are that should be deployed. You can use both -c and -f at the same time even though
 *      that doesn't really make sense. If you put just one CAP file in the folder then it behaves just like the -c option
 * -s:  The name of the system to which these files should be deployed. The name must be defined in the caas.conf of course.
 * -e:  Indicates whether or not the script should exit with a non-zero exit code when the package cannot be deployed. This
 *      can be because of 2 reasons: the package is already loaded and up to date OR the version you're trying to deploy is
 *      older than the version currently deployed. When not specified this parameter has the value true
 * 
 * The minimal parameters that must be specified are (-c or -f) AND -s.
 */

def cli = new CliBuilder(usage: 'run.cmd org/kisst/cordys/caas/deploy/DeployPackage [-h] [-f "capfolder" | -c "capfile"] -s "system"')
cli.h(longOpt: 'help'  , 'usage information', required: false)
cli.c(longOpt: 'cap', 'The CAP file that should be deployed', required: false, args: 1 )
cli.f(longOpt: 'folder', 'The folder containing the caps that should be deployed.', required: false, args: 1 )
cli.s(longOpt: 'system', 'The name of the parameter for the property file', required: true, args: 1 )
cli.e(longOpt: 'exitonexists', 'Indicates whether or not the script should exit with a non-zero exit code when the package cannot be deployed. Default value is true', required: false, args: 1 )

OptionAccessor opt = cli.parse(args);
if (!opt) {
    return
}

if (!opt.c && !opt.f) {
    System.err.println "Either the folder with CAPs must be specified or a specific cap should be entered"
    System.exit(1)
}

def exitOnExists = true
if (opt.e && opt.e.toLowerCase() == 'false') {
    exitOnExists = false
}
// Connect to the system
CordysSystem system = Caas.getSystem(opt.s)

def Map<String, CAPPackage> toBeDeployed = new LinkedHashMap<String, CAPPackage>()
def Map<String, CAPPackage> upToDate = new LinkedHashMap<String, CAPPackage>()
def Map<String, CAPPackage> outdated = new LinkedHashMap<String, CAPPackage>()

// Figure out the packages that should be loaded and figure out the sequence
if (opt.c) {
    def cf = new File(opt.c)
    if (!cf.exists()) {
        System.err.println "Defined cap file " + cf.absolutePath + " does not exist."
        System.exit(2)
    }

    //Add the CAP to the list of the to-be-deployed caps if it hasn't been deployed already.
    processCap(system, cf, toBeDeployed, upToDate, outdated)
}

//Add all the caps if a folder was specified
if (opt.f) {
    def folder = new File(opt.f)

    for (srcCap in folder.listFiles()) {
        processCap(system, srcCap, toBeDeployed, upToDate, outdated)
    }
}

Log.info '= Going to deploy ' + toBeDeployed.size() + ' packages'
Log.info '= Up to date packages: ' + upToDate.size() + ' packages'
Log.info '= Outdated packages: ' + outdated.size() + ' packages'

//Check if we should abort the script because the package either already is up to date or that a more recent version is already deployed.
if (exitOnExists) {
    if (upToDate.size() > 0) {
        println "Package " + upToDate.find{true}.key + " is up to date";
        System.exit(1);
    } else if (outdated.size() > 0) {
        println "Package " + upToDate.find{true}.key + " is older then the currently deployed version.";
        System.exit(2);
    }
}

Log.debug 'Original order: ' + toBeDeployed.keySet()

toBeDeployed = CAPPackage.fixOrder(toBeDeployed);
Log.debug "Loading sequence:"
toBeDeployed.keySet().each { Log.debug it }

// First upload all packages
toBeDeployed.values().each {
    if (it.status == EPackageStatus.not_loaded) {
        Log.info 'Uploading package ' + it.name + ' from file ' + it.file
        system.uploadCap(it.file.absolutePath)
    } else {
        Log.warn("Not uploading package as it already has status " + it.status);
    }
}

// Deploy the packages that are needed.
toBeDeployed.values().each {
    Log.info 'Deploying package ' + it.name + ' using 60 minutes as the timeout'
    system.deployCap(it.name, 60)
}


/**
 * This method processes the given cap file. It determines whether or not the package is to be loaded or that it is already loaded and up to date.
 * 
 * @param system The Cordys system to check.
 * @param srcCap The CAP file that should be loaded.
 * @param toBeDeployed The list to add the cap to in case it should be deployed.
 * @param upToDate The list to add the CAP to in case the version is already loaded.
 * @param outdated The list to which the package is added in case this version is older then the one currently deployed.
 */
def processCap(CordysSystem system, File srcCap, Map<String, CAPPackage> toBeDeployed, Map<String, CAPPackage> upToDate, Map<String, CAPPackage> outdated) {
    def ns = new groovy.xml.Namespace("http://schemas.cordys.com/ApplicationPackage")

    def cp = new CAPPackage(file: srcCap)

    //Now we have to do something tricky: we need to read the CAP package metadata to figure out the packacge name (the DN.
    def metadata = cp.getMetadata()

    //Find the package DN, version and build number of this package to be able to validate later on what version
    //is on the target system and if we can upload this package and/or deploy it.
    def name = metadata.'@name'
    def version = metadata[ns.Header][ns.Version].text()
    def buildNumber = metadata[ns.Header][ns.BuildNumber].text()
    def realVersion = "$version.$buildNumber".toString()

    Log.debug(name + " version $version.$buildNumber")
    Log.debug 'Dependencies: ' + cp.dependencies

    //Now we need to get the information on the target system (if the package is already loaded)
    def pkg = system.packages.getByName(name);
    if (pkg == null) {
        // Package is not loaded, so it is safe to load
        Log.debug 'Package ' + name + ' is not yet loaded'
        toBeDeployed.put(cp.name, cp);
    } else {
        // Package is already loaded. Need to double check the version numbers.
        if (pkg.info == null || pkg.status == EPackageStatus.incomplete) {
            Log.info 'Package ' + name + ' is incomplete'
            cp.status = EPackageStatus.incomplete
            toBeDeployed.put(cp.name, cp);
        } else {
            def loadedVersion = pkg.fullVersion
            Log.info 'Package ' + name + ' already loaded. Checking version ' + realVersion + " against loaded version " + loadedVersion
            if (loadedVersion == realVersion) {
                cp.status = EPackageStatus.loaded
                upToDate.put(cp.name, cp);
                Log.info '--> Package ' + name + ' is up-to-date'
            } else {
                //Check if the loaded version is more recent then the one that is being loaded.
                def mostRecent = mostRecentVersion([realVersion, loadedVersion])
                if (mostRecent == loadedVersion) {
                    outdated.put(cp.name, cp);
                    Log.info '--> Package ' + name + ' version ' + loadedVersion + ' is more recent then the version of the package that has to be loaded: ' + realVersion;
                } else {
                    //The version in the file is more recent, so we should load the package
                    Log.info '--> Package ' + name + ' version ' + realVersion+ ' is newer. Going to upload and deploy the package.';
                    toBeDeployed.put(cp.name, cp);
                }
            }
        }
    }
}

/**
 * This method returns which version is the most recent version. Note that it does NOT use a string compare, but it splits the version by 
 * the dots and compares the versions as integers.
 * 
 * @param versions The versions to compare.
 * @return The most recent version in the list
 */
String mostRecentVersion(List versions) {
    def sorted = versions.sort(false) { a, b ->

        List verA = a.tokenize('.')
        List verB = b.tokenize('.')

        def commonIndices = Math.min(verA.size(), verB.size())

        for (int i = 0; i < commonIndices; ++i) {
            def numA = verA[i].toInteger()
            def numB = verB[i].toInteger()

            if (numA != numB) {
                return numA <=> numB
            }
        }

        // If we got this far then all the common indices are identical, so whichever version is longer must be more recent
        verA.size() <=> verB.size()
    }

    sorted[-1]
}

