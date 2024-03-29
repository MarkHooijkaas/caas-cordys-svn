/**
 * Copyright 2008, 2009 Mark Hooijkaas This file is part of the Caas tool. The Caas tool is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version
 * 3 of the License, or (at your option) any later version. The Caas tool is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a copy of the GNU General Public License along with the Caas
 * tool. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kisst.cordys.caas.support;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kisst.cordys.caas.AuthenticatedUser;
import org.kisst.cordys.caas.ConnectionPoint;
import org.kisst.cordys.caas.CordysSystem;
import org.kisst.cordys.caas.Dso;
import org.kisst.cordys.caas.DsoType;
import org.kisst.cordys.caas.Organization;
import org.kisst.cordys.caas.OsProcess;
import org.kisst.cordys.caas.Package;
import org.kisst.cordys.caas.Role;
import org.kisst.cordys.caas.ServiceContainer;
import org.kisst.cordys.caas.ServiceGroup;
import org.kisst.cordys.caas.User;
import org.kisst.cordys.caas.WebService;
import org.kisst.cordys.caas.WebServiceInterface;
import org.kisst.cordys.caas.Xsd;
import org.kisst.cordys.caas.main.Environment;
import org.kisst.cordys.caas.util.Constants;
import org.kisst.cordys.caas.util.ReflectionUtil;
import org.kisst.cordys.caas.util.XmlNode;

/**
 * This is the base class for all kinds of Ldap Objects, except for CordysSystem, which is special This basically is just a
 * convenience class provding the getDn() and getSystem() webService so that not all sublcasses need to implement these again. It
 * is separate from the LdapObject class, because CordysSystem also is a LdapObject, but does can't use the dn and system (itself)
 * at construction time.
 */
public abstract class LdapObjectBase extends LdapObject
{
    /** Pattern to check if the DN identifies a loaded package */
    private static final Pattern PKG = Pattern.compile("^cn=([^,]+),cn=cordys,(cn=[^,]+,){0,1}o=.+$");
    /** Holds the system. */
    private final CordysSystem system;
    /** Holds the dn. */
    private final String dn;
    /** Holds the CN of the current entry. */
    private String cn;
    /** Holds the Constant ldapObjectTypes. */
    private static final HashMap<String, Class<?>> ldapObjectTypes = new HashMap<String, Class<?>>();
    /** Holds the list of dummy LDAP entries which are not stored as real LDAP objects, because they are just 'wrappers'. */
    private static final List<Pattern> dummyParents = new ArrayList<Pattern>();

    static
    {
        ldapObjectTypes.put("busauthenticatedusers", AuthenticatedUser.class);
        ldapObjectTypes.put("busauthenticationuser", AuthenticatedUser.class);
        ldapObjectTypes.put("busmethod", WebService.class);
        ldapObjectTypes.put("busmethodset", WebServiceInterface.class);
        ldapObjectTypes.put("busmethodtype", Xsd.class);
        ldapObjectTypes.put("organization", Organization.class);
        ldapObjectTypes.put("busorganizationalrole", Role.class);
        ldapObjectTypes.put("bussoapnode", ServiceGroup.class);
        ldapObjectTypes.put("bussoapprocessor", ServiceContainer.class);
        ldapObjectTypes.put("busorganizationaluser", User.class);
        ldapObjectTypes.put("busconnectionpoint", ConnectionPoint.class);
        ldapObjectTypes.put("busosprocess", OsProcess.class);
        ldapObjectTypes.put("datasource", Dso.class);
        ldapObjectTypes.put("datasourcetype", DsoType.class); // Lets not make it complex

        dummyParents.add(Pattern.compile("^cn=organizational users,o=[^,]+,cn=cordys,cn=[^,]+,o=[^,]+$"));
        dummyParents.add(Pattern.compile("^cn=organizational roles,o=[^,]+,cn=cordys,cn=[^,]+,o=[^,]+$"));
        dummyParents.add(Pattern.compile("^cn=soap nodes,o=[^,]+,cn=cordys,cn=[^,]+,o=[^,]+$"));
        dummyParents.add(Pattern.compile("^cn=method sets,o=[^,]+,cn=cordys,cn=[^,]+,o=[^,]+$"));
    }

    /**
     * Instantiates a new ldap object base.
     * 
     * @param parent The parent
     * @param dn The dn
     */
    protected LdapObjectBase(LdapObject parent, String dn)
    {
        super(parent);
        this.system = parent.getSystem();
        this.dn = dn;

        Matcher m = Constants.GET_CN.matcher(dn);

        if (m.find())
        {
            this.cn = m.group(1);
        }
    }

    /**
     * @see org.kisst.cordys.caas.support.CordysObject#getSystem()
     */
    @Override
    public CordysSystem getSystem()
    {
        return system;
    }

    /**
     * @see org.kisst.cordys.caas.support.CordysObject#getOrganization()
     */
    @Override
    public Organization getOrganization()
    {
        // We need to figure out what the organizational context is for this object. We determine this by examining the parent
        // until we find the organization object. If we can't determine it we'll use the system or of the CordysSystem.
        Organization retVal = null;

        LdapObject current = this;

        while (current != null && retVal == null)
        {
            if (current instanceof Organization)
            {
                retVal = (Organization) current;
                break;
            }

            // Get the parent
            CordysObject tmp = current.getParent();
            if (tmp instanceof LdapObjectBase)
            {
                current = (LdapObjectBase) tmp;
            }
            else
            {
                // The parent is no longer an LDAP object
                current = null;
                retVal = tmp.getOrganization();
            }
        }

        return retVal;
    }

    /**
     * @see org.kisst.cordys.caas.support.LdapObject#getDn()
     */
    @Override
    public String getDn()
    {
        return dn;
    }

    /**
     * @see org.kisst.cordys.caas.support.LdapObject#getCn()
     */
    @Override
    public String getCn()
    {
        return cn;
    }

    /**
     * This method creates the object.
     * 
     * @param system The system
     * @param dn The dn
     * @return The ldap object
     */
    public static LdapObject createObject(CordysSystem system, String dn)
    {
        XmlNode method = new XmlNode(Constants.GET_LDAP_OBJECT, Constants.XMLNS_LDAP);
        method.add("dn").setText(dn);

        XmlNode response = system.call(method);
        XmlNode entry = response.getChild("tuple/old/entry");
        return createObject(system, entry);
    }

    /**
     * This method creates the object.
     * 
     * @param system The system
     * @param entry The entry
     * @return The ldap object
     */
    public static LdapObject createObject(CordysSystem system, XmlNode entry)
    {
        if (entry == null)
        {
            return null;
        }

        String newdn = entry.getAttribute("dn");
        LdapObject parent = calcParent(system, entry.getAttribute("dn"));
        Class<?> resultClass = determineClass(system, entry);

        if (resultClass == Package.class)
        {
            if (newdn.startsWith("cn=licinfo,") || newdn.startsWith("cn=authenticated users,")
                    || newdn.startsWith("cn=consortia,"))
            {
                return null;
            }
        }

        if (resultClass == null)
        {
            throw new RuntimeException("could not determine class for entry " + entry);
        }

        Constructor<?> cons = ReflectionUtil.getConstructor(resultClass, new Class<?>[] { LdapObject.class, String.class });
        LdapObject result = (LdapObject) ReflectionUtil.createObject(cons, new Object[] { parent, newdn });
        result.setEntry(entry);
        return result;
    }

    /**
     * Determine class.
     * 
     * @param system The system
     * @param entry The entry
     * @return The class
     */
    private static Class<?> determineClass(CordysSystem system, XmlNode entry)
    {
        if (entry == null)
        {
            return null;
        }

        XmlNode objectclass = entry.getChild("objectclass");

        for (XmlNode o : objectclass.getChildren("string"))
        {
            Class<?> c = ldapObjectTypes.get(o.getText());

            if (c != null)
            {
                return c;
            }
        }

        return null;
    }

    /**
     * This method calculates the parent for the given entry. The calculation can be a bit tricky. Especially if the given DN is
     * part
     * 
     * @param system The system
     * @param dn The dn
     * @return The ldap object
     */
    private static LdapObject calcParent(CordysSystem system, String dn)
    {
        String origdn = dn;
        int pos;

        while ((pos = dn.indexOf(",")) >= 0)
        {
            dn = dn.substring(pos + 1);

            LdapObject parent = system.seekLdap(dn);

            if (parent != null)
            {
                return parent;
            }

            // There are a couple of entries that are in LDAP, but that we do not want to retrieve as we've not modeled them
            // directly into an object. Examples are like 'organizational users', 'soap nodes', 'method sets' or 'organizational
            // roles'. These are ignored to avoid unneeded calls to the GetLDAPObject for each underlying object.
            boolean dummy = false;
            for (Pattern p : dummyParents)
            {
                if (p.matcher(dn).matches())
                {
                    dummy = true;
                    break;
                }
            }

            if (dummy == true)
            {
                continue;
            }

            // Now check if the entry is a loaded package.
            Matcher m = PKG.matcher(dn);
            if (m.find())
            {
                // It is a package DN. So we need to have the cn, because that is the name of the package which we can use to look
                // up the runtime package.
                String cn = m.group(1);
                if (!"authenticated users".equals(cn))
                {
                    Package p = system.packages.getByName(cn);

                    // For some reason sometimes the package is not found
                    if (p == null)
                    {
                        Environment.warn("Could not find package with name " + cn);
                    }
                    else
                    {
                        system.rememberLdap(p.getRuntime());
                        return p.getRuntime();
                    }
                }
            }

            XmlNode entry = retrieveEntry(system, dn);

            if (entry == null) // could happen when restoring from a dump
            {
                continue;
            }

            Class<?> resultClass = determineClass(system, entry);

            if (resultClass != null)
            {
                return createObject(system, entry);
            }
        }
        throw new RuntimeException("Could not find a parent for " + origdn);
    }
}
