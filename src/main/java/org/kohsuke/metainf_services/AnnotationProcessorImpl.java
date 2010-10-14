/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.kohsuke.metainf_services;

import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"Since15"})
public class AnnotationProcessorImpl extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(MetaInfServices.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())      return false;

        Map<String,Set<String>> services = new HashMap<String, Set<String>>();

        // discover services from the current compilation sources
        for (TypeElement type : (Collection<TypeElement>)roundEnv.getElementsAnnotatedWith(MetaInfServices.class)) {
            TypeElement contract = getContract(type);
            if(contract==null)  continue; // error should have already been reported

            String cn = contract.getQualifiedName().toString();
            Set<String> v = services.get(cn);
            if(v==null)
                services.put(cn,v=new TreeSet<String>());
            v.add(type.getQualifiedName().toString());
        }

        // also load up any existing values, since this compilation may be partial
        Filer filer = processingEnv.getFiler();
        for (Map.Entry<String,Set<String>> e : services.entrySet()) {
            try {
                String contract = e.getKey();
                FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" +contract);
                BufferedReader r = new BufferedReader(new InputStreamReader(f.openInputStream(), "UTF-8"));
                String line;
                while((line=r.readLine())!=null)
                    e.getValue().add(line);
                r.close();
            } catch (FileNotFoundException x) {
                // doesn't exist
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR,"Failed to load existing service definition files: "+x);
            }
        }

        // now write them back out
        for (Map.Entry<String,Set<String>> e : services.entrySet()) {
            try {
                String contract = e.getKey();
                processingEnv.getMessager().printMessage(Kind.NOTE,"Writing META-INF/services/"+contract);
                FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" +contract);
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(f.openOutputStream(), "UTF-8"));
                for (String value : e.getValue())
                    pw.println(value);
                pw.close();
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR,"Failed to write service definition files: "+x);
            }
        }

        return false;
    }

    private TypeElement getContract(TypeElement type) {
        // explicitly specified?
        try {
            MetaInfServices a = type.getAnnotation(MetaInfServices.class);
            a.value();
            throw new AssertionError();
        } catch (MirroredTypeException e) {
            TypeMirror m = e.getTypeMirror();
            if (m.getKind()== TypeKind.VOID) {
                // contract inferred from the signature
                boolean hasBaseClass = type.getSuperclass().getKind()!=TypeKind.NONE && !isObject(type.getSuperclass());
                boolean hasInterfaces = !type.getInterfaces().isEmpty();
                if(hasBaseClass^hasInterfaces) {
                    if(hasBaseClass)
                        return (TypeElement)((DeclaredType)type.getSuperclass()).asElement();
                    return (TypeElement)((DeclaredType)type.getInterfaces().get(0)).asElement();
                }

                error(type, "Contract type was not specified, but it couldn't be inferred.");
                return null;
            }

            if (m instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) m;
                return (TypeElement)dt.asElement();
            } else {
                error(type, "Invalid type specified as the contract");
                return null;
            }
        }


    }

    private boolean isObject(TypeMirror t) {
        if (t instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) t;
            return((TypeElement)dt.asElement()).getQualifiedName().toString().equals("java.lang.Object");
        }
        return false;
    }

    private void error(Element source, String msg) {
        processingEnv.getMessager().printMessage(Kind.ERROR,msg,source);
    }
}
