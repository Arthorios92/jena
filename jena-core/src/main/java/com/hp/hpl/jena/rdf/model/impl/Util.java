/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.rdf.model.impl;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;

import org.apache.xerces.util.XMLChar ;

import com.hp.hpl.jena.JenaRuntime ;
import com.hp.hpl.jena.datatypes.RDFDatatype ;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype ;
import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.rdf.model.Literal ;
import com.hp.hpl.jena.shared.CannotEncodeCharacterException ;

/** Some utility functions.
 */
public class Util extends Object {

    /** Given an absolute URI, determine the split point between the namespace part
     * and the localname part.
     * If there is no valid localname part then the length of the
     * string is returned.
     * The algorithm tries to find the longest NCName at the end
     * of the uri, not immediately preceeded by the first colon
     * in the string.
     * @param uri
     * @return the index of the first character of the localname
     */
    public static int splitNamespace(String uri) {
        
        // XML Namespaces 1.0:
        // A qname name is NCName ':' NCName
        // NCName             ::=      NCNameStartChar NCNameChar*
        // NCNameChar         ::=      NameChar - ':'
        // NCNameStartChar    ::=      Letter | '_'
        // 
        // XML 1.0
        // NameStartChar      ::= ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] |
        //                        [#xD8-#xF6] | [#xF8-#x2FF] |
        //                        [#x370-#x37D] | [#x37F-#x1FFF] |
        //                        [#x200C-#x200D] | [#x2070-#x218F] |
        //                        [#x2C00-#x2FEF] | [#x3001-#xD7FF] |
        //                        [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]
        // NameChar           ::= NameStartChar | "-" | "." | [0-9] | #xB7 |
        //                        [#x0300-#x036F] | [#x203F-#x2040]
        // Name               ::= NameStartChar (NameChar)*
        
        char ch;
        int lg = uri.length();
        if (lg == 0)
            return 0;
        int i = lg-1 ;
        for ( ; i >= 1 ; i--) {
            ch = uri.charAt(i);
            if (notNameChar(ch)) break;
        }
        
        int j = i + 1 ;

        if ( j >= lg )
            return lg ;
        
        // Check we haven't split up a %-encoding.
        if ( j >= 2 && uri.charAt(j-2) == '%' )
            j = j+1 ;
        if ( j >= 1 && uri.charAt(j-1) == '%' )
            j = j+2 ;
        
        // Have found the leftmost NCNameChar from the
        // end of the URI string.
        // Now scan forward for an NCNameStartChar
        // The split must start with NCNameStart.
        for (; j < lg; j++) {
            ch = uri.charAt(j);
//            if (XMLChar.isNCNameStart(ch))
//                break ;
            if (XMLChar.isNCNameStart(ch))
            {
                // "mailto:" is special.
                // Keep part after mailto: at least one charcater.
                // Do a quick test before calling .startsWith
                // OLD: if ( uri.charAt(j - 1) == ':' && uri.lastIndexOf(':', j - 2) == -1)
                if ( j == 7 && uri.startsWith("mailto:"))
                    continue; // split "mailto:me" as "mailto:m" and "e" !
                else
                    break;
            }
        }
        return j;
    }

    /**
	    answer true iff this is not a legal NCName character, ie, is
	    a possible split-point start.
    */
    public static boolean notNameChar( char ch )
        { return !XMLChar.isNCName( ch ); }

    protected static Pattern standardEntities = 
    	   Pattern.compile( "&|<|>|\t|\n|\r|\'|\"" );
    
    public static String substituteStandardEntities( String s )
        {
        if (standardEntities.matcher( s ).find())
            {
            return substituteEntitiesInElementContent( s )
                .replaceAll( "'", "&apos;" )
                .replaceAll( "\t","&#9;" )
                .replaceAll( "\n", "&#xA;" )
                .replaceAll( "\r", "&#xD;" )
                .replaceAll( "\"", "&quot;" )
                ;
            }
        else
            return s;
        }
    
    protected static Pattern entityValueEntities = 
 	   Pattern.compile( "&|%|\'|\"" );
 
   public static String substituteEntitiesInEntityValue( String s )
     {
     if (entityValueEntities.matcher( s ).find())
         {
         return s
             .replaceAll( "&","&amp;" )
             .replaceAll( "'", "&apos;" )
             .replaceAll( "%", "&#37;" )
             .replaceAll( "\"", "&quot;" )
             ;
         }
     else
         return s;
     }
    protected static Pattern elementContentEntities = Pattern.compile( "<|>|&|[\0-\37&&[^\n\t]]|\uFFFF|\uFFFE" );
    /**
        Answer <code>s</code> modified to replace &lt;, &gt;, and &amp; by
        their corresponding entity references. 
        
    <p>
        Implementation note: as a (possibly misguided) performance hack, 
        the obvious cascade of replaceAll calls is replaced by an explicit
        loop that looks for all three special characters at once.
    */
    public static String substituteEntitiesInElementContent( String s ) 
        {
        Matcher m = elementContentEntities.matcher( s );
        if (!m.find())
            return s;
        else
            {
            int start = 0;
            StringBuilder result = new StringBuilder();
            do
                {
                result.append( s.substring( start, m.start() ) );
                char ch = s.charAt( m.start() );
                switch ( ch )
                {
                    case '\r': result.append( "&#xD;" ); break;
                    case '<': result.append( "&lt;" ); break;
                    case '&': result.append( "&amp;" ); break;
                    case '>': result.append( "&gt;" ); break;
                    default: throw new CannotEncodeCharacterException( ch, "XML" );
                }
                start = m.end();
                } while (m.find( start ));
            result.append( s.substring( start ) );
            return result.toString();
            }
        }

    public static String replace(String s, String oldString, String newString) {
        return s.replace(oldString, newString) ;
    }

    /**
     * A Node is a simple string if: 
     * <li>(RDF 1.0) No datatype and no language tag.
     * <li>(RDF 1.1) xsd:string
     */
    public static boolean isSimpleString(Node n) {
        RDFDatatype dt = n.getLiteralDatatype() ;
        if ( dt == null )
            return !isLangString(n) ;
        if ( JenaRuntime.isRDF11 )
            return dt.equals(XSDDatatype.XSDstring) ;
        return false ;
    }

    /**
     * A Node is a language string if it has a language tag. 
     * (RDF 1.0 and RDF 1.1)
     */
    public static boolean isLangString(Node n) {
        String lang = n.getLiteralLanguage() ;
        if ( lang == null )
            return false ;
        return !lang.equals("") ;
    }

    
    /** Return true if the literal should be written as a string, without datatype or lang. (RDF 1.0 and RDF 1.1) */ 
    public static boolean isSimpleString(Literal lit) {
        if ( lit.getDatatypeURI() == null ) {
            return ! isLangString(lit) ;
        }
        if ( JenaRuntime.isRDF11 && lit.getDatatypeURI().equals(XSDDatatype.XSDstring.getURI()) )
            return true;
        return false ;
    }
    
    /** Return true if the literals shodul be written with a language string. (RDF 1.0 and RDF 1.1) */
    public static boolean isLangString(Literal lit) {
        String lang = lit.getLanguage() ;
        if ( lang == null )
            return false ;
        return ! lang.equals("") ;
    }

}
