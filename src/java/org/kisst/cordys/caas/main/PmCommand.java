/**
Copyright 2008, 2009 Mark Hooijkaas

This file is part of the Caas tool.

The Caas tool is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

The Caas tool is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the Caas tool.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.kisst.cordys.caas.main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.kisst.cordys.caas.Caas;
import org.kisst.cordys.caas.CordysSystem;
import org.kisst.cordys.caas.Organization;
import org.kisst.cordys.caas.pm.CaasPackage;
import org.kisst.cordys.caas.pm.Template;
import org.kisst.cordys.caas.util.FileUtil;


public class PmCommand extends CompositeCommand {
	private abstract class HostCommand extends CommandBase {
		public HostCommand(String usage, String summary) { super(usage, summary); }
		protected final Cli cli=new Cli();
		private final Cli.StringOption system= cli.stringOption("s", "system", "the system to use", null);
		private final Cli.StringOption org= cli.stringOption("o", "organization", "the organization to use", null);
				
		protected CordysSystem getSystem() { return Caas.getSystem(Caas.defaultSystem); }
		protected Organization getOrg(String defaultOrg) {
			if (org.isSet())				
				return getSystem().org.getByName(org.get());
			else
				return getSystem().org.getByName(defaultOrg);
		}
		protected String[] checkArgs(String[] args) {
			args=cli.parse(args);
			if (system.isSet())
				Caas.defaultSystem=system.get();
				
			return args;
		}
		@Override public String getHelp() {
			return "\nOPTIONS\n"+cli.getSyntax("\t");
		}
	}
	
	private Command check=new HostCommand("[options] <ccm file>", "validates the given install info") {
		@Override public void run(String[] args) {
			args=checkArgs(args);
			CaasPackage p=Caas.pm.p(args[0]);
			boolean result=p.check(getOrg(p.getDefaultOrgName()));
			System.out.println(result);
		}
	};
	private Command configure=new HostCommand("[options] <ccm file>", "installs the given isvp") {
		@Override public void run(String[] args) { 
			args=checkArgs(args);
			CaasPackage p=Caas.pm.p(args[0]);
			p.configure(getOrg(p.getDefaultOrgName()));
		}
	};
	private Command purge=new HostCommand("[options] <ccm file>", "removes the given isvp") {
		@Override public void run(String[] args) { 
			args=checkArgs(args);
			CaasPackage p=Caas.pm.p(args[0]);
			p.purge(getOrg(p.getDefaultOrgName())); 	
		}
	};
	private Command template=new HostCommand("[options] <template file>", "create a template based on the given organization") {
		private final Cli.StringOption isvpName= cli.stringOption("i", "isvpName", "the isvpName to use for custom content", null);
		@Override public void run(String[] args) { 
			args=checkArgs(args);
			
			String orgz = System.getProperty("template.org");
			//Template templ = new Template(getOrg(null), isvpName.get());
			Template templ = new Template(getOrg(orgz), isvpName.get());
			templ.save(args[0]);
		}
	};

	/**
	 * Implement the apply method
	 * 
	 * TODO : Read the properties file and pass the hashmap
	 */
	private Command create=new HostCommand("[options] <template file>", "create elements in an organization based on the given template") {
		@Override public void run(String[] args) { 
			args=checkArgs(args);
			Template templ=new Template(FileUtil.loadString(args[0]));			
			String orgz = System.getProperty("create.org");
			Map<String,String> map = loadSystemProperties(this.getSystem().getName());
			templ.apply(getOrg(orgz), map);
		}
	};
	
	public PmCommand() {
		super("caas pm","run a caas package manager command"); 
		//options.addOption("o", "org", true, "override the default organization");
		commands.put("check", check);
		commands.put("configure", configure);
		commands.put("purge", purge);
		commands.put("template", template);
		commands.put("create", create);
	}
	
	private Map<String, String> loadSystemProperties(String systemName){

		String fileName=null; 
		Map<String, String> map=null;
		String fileName1 = Environment.get().getProp("system."+systemName+".properties.file", null);
		String fileName2 = "caas.properties";
		String fileName3 = System.getProperty("user.home")+"/config/caas/caas.properties";
		Properties props = new Properties();
		fileName1 = fileName1.replace("\\", "/");
		fileName3 = fileName3.replace("\\", "/");
		
		String[] fileNames = new String[]{fileName1, fileName2, fileName3};
		for(String  aFileName:fileNames){
			if(isFileExists(aFileName)){
				fileName = aFileName;
				break;
			}
		}
		if(fileName!=null){
			FileUtil.load(props, fileName);
			map = new HashMap<String, String>((Map) props);	
		}else
			Environment.get().warn("No file is configured for property 'system."+systemName+".properties.file' in caas.conf. Make sure there are no variables to be resolved in template file.");
		
		return map;
	}
	private boolean isFileExists(String fileName){
		if(fileName==null)
			return false;
		File file = new File(fileName);
		return file.exists();
	}
}
