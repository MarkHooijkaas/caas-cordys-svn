//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.06.21 at 10:05:41 AM CEST 
//


package org.kisst.caas._2_0.template;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for XMLStoreVersion.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="XMLStoreVersion">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="isv"/>
 *     &lt;enumeration value="organization"/>
 *     &lt;enumeration value="user"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "XMLStoreVersion")
@XmlEnum
public enum XMLStoreVersion {

    @XmlEnumValue("isv")
    ISV("isv"),
    @XmlEnumValue("organization")
    ORGANIZATION("organization"),
    @XmlEnumValue("user")
    USER("user");
    private final String value;

    XMLStoreVersion(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static XMLStoreVersion fromValue(String v) {
        for (XMLStoreVersion c: XMLStoreVersion.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
