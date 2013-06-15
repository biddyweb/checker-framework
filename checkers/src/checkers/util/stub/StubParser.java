package checkers.util.stub;

import java.io.InputStream;
import java.util.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import javacutils.AnnotationUtils;
import javacutils.ElementUtils;
import javacutils.ErrorReporter;

import checkers.quals.FromStubFile;
import checkers.source.SourceChecker;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.*;
import checkers.util.AnnotationBuilder;

import japa.parser.JavaParser;
import japa.parser.ast.*;
import japa.parser.ast.body.*;
import japa.parser.ast.expr.*;
import japa.parser.ast.type.*;

/*>>>
import checkers.nullness.quals.*;
*/

// Main entry point is:
// parse(Map<Element, AnnotatedTypeMirror>, Map<Element, Set<AnnotationMirror>>)

public class StubParser {

    /**
     * Whether to print warnings about types/members that were not found.
     * The warning is about whether a class/field in the stub file is not
     * found on the user's real classpath.  Since the stub file may contain
     * packages that are not on the classpath, this can be OK, so default to
     * false.
     */
    private boolean warnIfNotFound = false;

    private boolean debugStubParser = false;

    /** The file being parsed (makes error messages more informative). */
    private final String filename;

    private final IndexUnit index;
    private final ProcessingEnvironment processingEnv;
    private final AnnotatedTypeFactory atypeFactory;
    private final Elements elements;

    /**
     * The supported annotations. Keys are simple (unqualified) names.
     * (This may be a problem in the unlikely occurrence that a
     * type-checker supports two annotations with the same simple name.)
     */
    private final Map<String, AnnotationMirror> supportedAnnotations;

    /**
     * A list of imports that are not annotation types.
     * Used for importing enums.
     */
    private final List<String> imports;

    /**
     * Mapping of a field access expression that has already been encountered
     * to the resolved variable element.
     */
    private final Map<FieldAccessExpr, VariableElement> faexprcache;
   
    /**
     * Mapping of a name access expression that has already been encountered
     * to the resolved variable element.
     */
     private final Map<NameExpr, VariableElement> nexprcache;
     /**
     * Annotation to added to every method and constructor in the stub file.
     */
     private final AnnotationMirror fromStubFile;


    /**
     * 
     * @param filename name of stub file
     * @param inputStream of stub file to parse
     * @param factory  AnnotatedtypeFactory to use
     * @param env ProcessingEnviroment to use
     */
    public StubParser(String filename, InputStream inputStream,
            AnnotatedTypeFactory factory, ProcessingEnvironment env) {
        this.filename = filename;
        IndexUnit parsedindex;
        try {
            parsedindex = JavaParser.parse(inputStream);
        } catch (Exception e) {
            ErrorReporter.errorAbort("StubParser: exception from JavaParser.parse for file " + filename, e);
            parsedindex = null; // dead code, but needed for def. assignment checks
        }
        this.index = parsedindex;
        this.atypeFactory = factory;
        this.processingEnv = env;
        this.elements = env.getElementUtils();
        imports = new ArrayList<String>();
        
        // getSupportedAnnotations uses these for warnings
        Map<String, String> options = env.getOptions();
        this.warnIfNotFound = options.containsKey("stubWarnIfNotFound");
        this.debugStubParser = options.containsKey("stubDebug");
        
        // getSupportedAnnotations also sets imports. This should be refactored to be nicer.
        supportedAnnotations = getSupportedAnnotations();
        if (supportedAnnotations.isEmpty()) {
            stubWarning("No supported annotations found! This likely means your stub file doesn't import them correctly.");
        }
        faexprcache = new HashMap<FieldAccessExpr, VariableElement>();
        nexprcache = new HashMap<NameExpr, VariableElement>();
        
        this.fromStubFile =   AnnotationUtils.fromClass(elements, FromStubFile.class);
    }



    /** All annotations defined in the package.  Keys are simple names. */
    private Map<String, AnnotationMirror> annosInPackage(PackageElement packageElement) {
        return createImportedAnnotationsMap(ElementFilter.typesIn(packageElement.getEnclosedElements()));
    }
    /** All annotations declarations nested inside of a class. */
    private Map<String, AnnotationMirror> annosInType(TypeElement typeElement) {
        return createImportedAnnotationsMap(ElementFilter.typesIn(typeElement.getEnclosedElements()));
    }

    private Map<String, AnnotationMirror>  createImportedAnnotationsMap(List<TypeElement> typeElements) {
        Map<String, AnnotationMirror> r = new HashMap<String, AnnotationMirror>();
        for (TypeElement typeElm : typeElements) {
            if (typeElm.getKind() == ElementKind.ANNOTATION_TYPE) {
                AnnotationMirror anno = AnnotationUtils.fromName(elements, typeElm.getQualifiedName());
                putNew(r, typeElm.getSimpleName().toString(), anno);
            }
        }
        return r;
    }
    
    /**
     * Get all members of a Type that are useful in a stub file.
     * Currently these are values of enums, or compile time constants.
     * 
     * @return a list fully qualified member names
     */
    private List<String> getImportableMembers(TypeElement typeElement) {
        List<String> result = new ArrayList<String>();
        List<VariableElement> memberElements = ElementFilter.fieldsIn(typeElement.getEnclosedElements());
        for (VariableElement varElement : memberElements) {
            if (varElement.getConstantValue() != null 
                    || varElement.getKind() == ElementKind.ENUM_CONSTANT) {
            	
                result.add(String.format("%s.%s", typeElement.getQualifiedName().toString(), 
                		varElement.getSimpleName().toString()));
            }
        }
        
        return result;
    }
    
    /** @see #supportedAnnotations */
    private Map<String, AnnotationMirror> getSupportedAnnotations() {
        assert !index.getCompilationUnits().isEmpty();
        CompilationUnit cu = index.getCompilationUnits().get(0);

        Map<String, AnnotationMirror> result = new HashMap<String, AnnotationMirror>();

        if (cu.getImports() == null)
            return result;

        for (ImportDeclaration importDecl : cu.getImports()) {
            String imported = importDecl.getName().toString();
            try {
                if (importDecl.isAsterisk()) {
                	// Members of a Type (according to jls)
                    if(importDecl.isStatic()) {
                    	TypeElement element = findType(imported, "Imported type not found");
                    	if (element != null) {
                    		// Find nested annotations
                            // Find compile time constant fields, or values of an enum
                    		putAllNew(result, annosInType(element));
                    		imports.addAll(getImportableMembers(element));
                    	}
                    // Members of a package (according to jls)
                    } else {
                    	PackageElement element = findPackage(imported);
                    	if (element != null) {
                    		putAllNew(result, annosInPackage(element));
                    	}
                    }
                } else {
                    final TypeElement importType = elements.getTypeElement(imported);
                    
                    // Class or nested class (according to jls), but we can't resolve
                    if (importType == null && !importDecl.isStatic()) {
                    	if (warnIfNotFound || debugStubParser) {
                            stubWarning("Imported type not found: " + imported);
                		}
                    
                    // Nested Field
                    } else if (importType == null) {
                		Pair<String, String> typeParts = partitionQualifiedName(imported);
                		String type = typeParts.first;
                		String fieldName = typeParts.second;
                        TypeElement enclType = findType(type, 
                        		String.format("Enclosing type of static field %s not found", fieldName));
                        
                        if (enclType != null) {
                        	if (findFieldElement(enclType, fieldName) != null) {
                        		imports.add(imported);
                        	}
                        }
                        
                    // Single annotation or nested annotation
                    } else if (importType.getKind() == ElementKind.ANNOTATION_TYPE) {
                    	AnnotationMirror anno = AnnotationUtils.fromName(elements, imported);
                        if (anno != null ) {
                            Element annoElt = anno.getAnnotationType().asElement();
                            putNew(result, annoElt.getSimpleName().toString(), anno);
                        } else {
                            if (warnIfNotFound || debugStubParser) {
                                stubWarning("Could not load import: " + imported);
                            }
                        }	
                        
                    // Class or nested class
                    } else {
                		imports.add(imported);
                	}
                }
            } catch (AssertionError error) {
                stubWarning("" + error);
            }
        }
        return result;
    }	
    
	// The main entry point.  Side-effects the arguments.
    public void parse(Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        parse(this.index, atypes, declAnnos);
    }

    private void parse(IndexUnit index, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        for (CompilationUnit cu : index.getCompilationUnits())
            parse(cu, atypes, declAnnos);
    }

    private CompilationUnit theCompilationUnit;

    private void parse(CompilationUnit cu, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        theCompilationUnit = cu;
        final String packageName;
        if (cu.getPackage() == null) {
            packageName = null;
        } else {
            packageName = cu.getPackage().getName().toString();
            parsePackage(cu.getPackage(), atypes, declAnnos);
        }
        if (cu.getTypes() != null) {
            for (TypeDeclaration typeDecl : cu.getTypes())
                parse(typeDecl, packageName, atypes, declAnnos);
        }
    }

    private void parsePackage(PackageDeclaration packDecl, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        assert(packDecl != null);
        String packageName = packDecl.getName().toString();
        Element elem = elements.getPackageElement(packageName);
        // If the element lookup fails, it's because we have an annotation for a package that isn't on the classpath, which is fine.
        if (elem != null) {
            annotateDecl(declAnnos, elem, packDecl.getAnnotations());
        }
        // TODO: Handle atypes???
    }

    // typeDecl's name may be a binary name such as "A$B".
    // That is a hack because the StubParser does not handle nested classes.
    private void parse(TypeDeclaration typeDecl, String packageName, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        // Fully-qualified name of the type being parsed
        String typeName = (packageName == null ? "" : packageName + ".") + typeDecl.getName().replace('$', '.');
        TypeElement typeElt = elements.getTypeElement(typeName);
        // couldn't find type.  not in class path
        // TODO: Should throw exception?!
        if (typeElt == null) {
            if (warnIfNotFound || debugStubParser)
                stubWarning("Type not found: " + typeName);
            return;
        }

        if (typeElt.getKind() == ElementKind.ENUM) {
            if (warnIfNotFound || debugStubParser)
                stubWarning("Skipping enum type: " + typeName);
        } else if (typeElt.getKind() == ElementKind.ANNOTATION_TYPE) {
            if (warnIfNotFound || debugStubParser)
                stubWarning("Skipping annotation type: " + typeName);
        } else if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            parseType((ClassOrInterfaceDeclaration)typeDecl, typeElt, atypes, declAnnos);
        } // else it's an EmptyTypeDeclaration.  TODO:  An EmptyTypeDeclaration can have annotations, right?

        Map<Element, BodyDeclaration> elementsToDecl = getMembers(typeElt, typeDecl);
        for (Map.Entry<Element, BodyDeclaration> entry : elementsToDecl.entrySet()) {
            final Element elt = entry.getKey();
            final BodyDeclaration decl = entry.getValue();
            if (elt.getKind().isField())
                parseField((FieldDeclaration)decl, (VariableElement)elt, atypes, declAnnos);
            else if (elt.getKind() == ElementKind.CONSTRUCTOR)
                parseConstructor((ConstructorDeclaration)decl, (ExecutableElement)elt, atypes, declAnnos);
            else if (elt.getKind() == ElementKind.METHOD)
                parseMethod((MethodDeclaration)decl, (ExecutableElement)elt, atypes, declAnnos);
            else { /* do nothing */
                System.err.println("StubParser ignoring: " + elt);
            }
        }
    }

    private void parseType(ClassOrInterfaceDeclaration decl, TypeElement elt, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        annotateDecl(declAnnos, elt, decl.getAnnotations());
        AnnotatedDeclaredType type = atypeFactory.fromElement(elt);
        annotate(type, decl.getAnnotations());
        {
            List<? extends AnnotatedTypeMirror> typeArguments = type.getTypeArguments();
            List<TypeParameter> typeParameters = decl.getTypeParameters();
            /// It can be the case that args=[] and params=null.
            // if ((typeParameters == null) != (typeArguments == null)) {
            //     throw new Error(String.format("parseType (%s, %s): inconsistent nullness for args and params%n  args = %s%n  params = %s%n", decl, elt, typeArguments, typeParameters));
            // }
            if ((typeParameters == null) && (typeArguments.size() != 0)) {
                // TODO: Class EventListenerProxy in Java 6 does not have type parameters, but in Java 7 does.
                // To handle both with one specification, we currently ignore the problem.
                // Investigate what a cleaner solution is, e.g. having a separate Java 7 specification that overrides
                // the Java 6 specification.
                // System.out.printf("Dying.  theCompilationUnit=%s%n", theCompilationUnit);
                if (debugStubParser) {
                    System.out.printf(String.format("parseType:  mismatched sizes for params and args%n  decl=%s%n  typeParameters=%s%n  elt=%s (%s)%n  type=%s (%s)%n  typeArguments (size %d)=%s%n  theCompilationUnit=%s%nEnd of Message%n",
                                              decl, typeParameters,
                                              elt, elt.getClass(), type, type.getClass(), typeArguments.size(), typeArguments,
                                              theCompilationUnit));
                    System.out.flush();
                }
                /*
                throw new Error(String.format("parseType:  mismatched sizes for params and args%n  decl=%s%n  typeParameters=%s%n  elt=%s (%s)%n  type=%s (%s)%n  typeArguments (size %d)=%s%n",
                                              decl, typeParameters,
                                              elt, elt.getClass(), type, type.getClass(), typeArguments.size(), typeArguments));
                 */
            }
            if ((typeParameters != null) && (typeParameters.size() != typeArguments.size())) {
                // TODO: decide how severe this problem really is; see comment above.
                // System.out.printf("Dying.  theCompilationUnit=%s%n", theCompilationUnit);
                if (debugStubParser) {
                    System.out.printf(String.format("parseType:  mismatched sizes for params and args%n  decl=%s%n  typeParameters (size %d)=%s%n  elt=%s (%s)%n  type=%s (%s)%n  typeArguments (size %d)=%s%n  theCompilationUnit=%s%nEnd of Message%n",
                                              decl, typeParameters.size(), typeParameters,
                                              elt, elt.getClass(), type, type.getClass(), typeArguments.size(), typeArguments,
                                              theCompilationUnit));
                    System.out.flush();
                }
                /*
                throw new Error(String.format("parseType:  mismatched sizes for params and args%n  decl=%s%n  typeParameters (size %d)=%s%n  elt=%s (%s)%n  type=%s (%s)%n  typeArguments (size %d)=%s%n",
                                              decl, typeParameters.size(), typeParameters,
                                              elt, elt.getClass(), type, type.getClass(), typeArguments.size(), typeArguments));
                */
            }
        }
        annotateParameters(type.getTypeArguments(), decl.getTypeParameters());
        annotateSupertypes(decl, type);
        putNew(atypes, elt, type);
    }

    private void annotateSupertypes(ClassOrInterfaceDeclaration typeDecl, AnnotatedDeclaredType type) {
        if (typeDecl.getExtends() != null) {
            for (ClassOrInterfaceType superType : typeDecl.getExtends()) {
                AnnotatedDeclaredType foundType = findType(superType, type.directSuperTypes());
                assert foundType != null : "StubParser: could not find superclass " + superType + " from type " + type;
                if (foundType != null) annotate(foundType, superType);
            }
        }
        if (typeDecl.getImplements() != null) {
            for (ClassOrInterfaceType superType : typeDecl.getImplements()) {
                AnnotatedDeclaredType foundType = findType(superType, type.directSuperTypes());
                // TODO: Java 7 added a few AutoCloseable superinterfaces to classes.
                // We specify those as superinterfaces in the jdk.astub file. Let's ignore
                // this addition to be compatible with Java 6.
                assert foundType != null || (superType.toString().equals("AutoCloseable") || superType.toString().equals("java.io.Closeable") || superType.toString().equals("Closeable")) :
                    "StubParser: could not find superinterface " + superType + " from type " + type;
                if (foundType != null) annotate(foundType, superType);
            }
        }
    }

    private void parseMethod(MethodDeclaration decl, ExecutableElement elt,
            Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        annotateDecl(declAnnos, elt, decl.getAnnotations());
        // StubParser parses all annotations in type annotation position as type annotations
        annotateDecl(declAnnos, elt, decl.getType().getAnnotations());
        addDeclAnnotations(declAnnos, elt);


        AnnotatedExecutableType methodType = atypeFactory.fromElement(elt);
        annotateParameters(methodType.getTypeVariables(), decl.getTypeParameters());
        annotate(methodType.getReturnType(), decl.getType());

        List<Parameter> params = decl.getParameters();
        List<? extends VariableElement> paramElts = elt.getParameters();
        List<? extends AnnotatedTypeMirror> paramTypes = methodType.getParameterTypes();

        for (int i = 0; i < methodType.getParameterTypes().size(); ++i) {
            VariableElement paramElt = paramElts.get(i);
            AnnotatedTypeMirror paramType = paramTypes.get(i);
            Parameter param = params.get(i);

            annotateDecl(declAnnos, paramElt, param.getAnnotations());
            annotateDecl(declAnnos, paramElt, param.getType().getAnnotations());

            if (param.isVarArgs()) {
                // workaround
                assert paramType.getKind() == TypeKind.ARRAY;
                annotate(((AnnotatedArrayType)paramType).getComponentType(), param.getType());
            } else {
                annotate(paramType, param.getType());
            }
        }

        annotate(methodType.getReceiverType(), decl.getReceiverAnnotations());

        putNew(atypes, elt, methodType);
    }

    /**
     * Adds a declAnnotation to every method in the stub file.
     * 
     * @param declAnnos
     * @param elt
     */
    private void addDeclAnnotations(
            Map<String, Set<AnnotationMirror>> declAnnos, ExecutableElement elt) {
        if (fromStubFile != null) {
            Set<AnnotationMirror> annos = declAnnos.get(ElementUtils
                    .getVerboseName(elt));
            if (annos == null) {
                annos = AnnotationUtils.createAnnotationSet();
                declAnnos.put(ElementUtils.getVerboseName(elt), annos);
            }
            annos.add(fromStubFile);
        }
    }

    /**
     * List of all array component types. 
     * Example input: int[][] 
     * Example output: int, int[], int[][]
     */
    private List<AnnotatedTypeMirror> arrayAllComponents(AnnotatedArrayType atype) {
        LinkedList<AnnotatedTypeMirror> arrays = new LinkedList<AnnotatedTypeMirror>();

        AnnotatedTypeMirror type = atype;
        while (type.getKind() == TypeKind.ARRAY) {
            arrays.addFirst(type);

            type = ((AnnotatedArrayType)type).getComponentType();
        }

        arrays.add(type);
        return arrays;
    }

    private void annotateAsArray(AnnotatedArrayType atype, ReferenceType typeDef) {
        List<AnnotatedTypeMirror> arrayTypes = arrayAllComponents(atype);
        assert typeDef.getArrayCount() == arrayTypes.size() - 1 ||
                // We want to allow simply using "Object" as return type of a
                // method, regardless of what the real type is.
                typeDef.getArrayCount() == 0 :
            "Mismatched array lengths; typeDef: " + typeDef.getArrayCount() +
            " vs. arrayTypes: " + (arrayTypes.size() - 1) +
                    "\n  typedef: " + typeDef + "\n  arraytypes: " + arrayTypes;
        /* Separate TODO: the check for zero above ensures that "Object" can be
         * used as return type, even when the real method uses something else.
         * However, why was this needed for the RequiredPermissions declaration annotation?
         * It looks like the StubParser ignored the target for annotations.
         */
        for (int i = 0; i < typeDef.getArrayCount(); ++i) {
            List<AnnotationExpr> annotations = typeDef.getAnnotationsAtLevel(i);
            if (annotations != null) {
                annotate(arrayTypes.get(i), annotations);
            }
        }

        // handle generic type on base
        annotate(arrayTypes.get(arrayTypes.size() - 1), typeDef.getAnnotations());
    }

    private ClassOrInterfaceType unwrapDeclaredType(Type type) {
        if (type instanceof ClassOrInterfaceType)
            return (ClassOrInterfaceType)type;
        else if (type instanceof ReferenceType
                && ((ReferenceType)type).getArrayCount() == 0)
            return unwrapDeclaredType(((ReferenceType)type).getType());
        else
            return null;
    }

    private void annotate(AnnotatedTypeMirror atype, Type typeDef) {
        if (atype.getKind() == TypeKind.ARRAY) {
            annotateAsArray((AnnotatedArrayType)atype, (ReferenceType)typeDef);
            return;
        }
        if (typeDef.getAnnotations() != null)
            annotate(atype, typeDef.getAnnotations());
        ClassOrInterfaceType declType = unwrapDeclaredType(typeDef);
        if (atype.getKind() == TypeKind.DECLARED
                && declType != null) {
            AnnotatedDeclaredType adeclType = (AnnotatedDeclaredType)atype;
            if (declType.getTypeArgs() != null
                    && !declType.getTypeArgs().isEmpty()
                    && adeclType.isParameterized()) {
                assert declType.getTypeArgs().size() == adeclType.getTypeArguments().size();
                for (int i = 0; i < declType.getTypeArgs().size(); ++i) {
                    annotate(adeclType.getTypeArguments().get(i),
                            declType.getTypeArgs().get(i));
                }
            }
        } else if (atype.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType wildcardType = (AnnotatedWildcardType)atype;
            WildcardType wildcardDef = (WildcardType)typeDef;
            if (wildcardDef.getExtends() != null) {
                annotate(wildcardType.getExtendsBound(), wildcardDef.getExtends());
            } else if (wildcardDef.getSuper() != null) {
                annotate(wildcardType.getSuperBound(), wildcardDef.getSuper());
            }
        }
    }

    private void parseConstructor(ConstructorDeclaration decl,
            ExecutableElement elt, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        annotateDecl(declAnnos, elt, decl.getAnnotations());
        AnnotatedExecutableType methodType = atypeFactory.fromElement(elt);
        addDeclAnnotations(declAnnos, elt);

        for (int i = 0; i < methodType.getParameterTypes().size(); ++i) {
            AnnotatedTypeMirror paramType = methodType.getParameterTypes().get(i);
            Parameter param = decl.getParameters().get(i);
            annotate(paramType, param.getType());
        }

        annotate(methodType.getReceiverType(), decl.getReceiverAnnotations());

        putNew(atypes, elt, methodType);
    }

    private void parseField(FieldDeclaration decl,
            VariableElement elt, Map<Element, AnnotatedTypeMirror> atypes, Map<String, Set<AnnotationMirror>> declAnnos) {
        annotateDecl(declAnnos, elt, decl.getAnnotations());
        // StubParser parses all annotations in type annotation position as type annotations
        annotateDecl(declAnnos, elt, decl.getType().getAnnotations());
        AnnotatedTypeMirror fieldType = atypeFactory.fromElement(elt);
        annotate(fieldType, decl.getType());
        putNew(atypes, elt, fieldType);
    }

    private void annotate(AnnotatedTypeMirror type, List<AnnotationExpr> annotations) {
        if (annotations == null)
            return;
        for (AnnotationExpr annotation : annotations) {
            AnnotationMirror annoMirror = getAnnotation(annotation, supportedAnnotations, processingEnv);
            if (annoMirror != null)
                type.replaceAnnotation(annoMirror);
        }
    }

    private void annotateDecl(Map<String, Set<AnnotationMirror>> declAnnos, Element elt, List<AnnotationExpr> annotations) {
        if (annotations == null)
            return;
        Set<AnnotationMirror> annos = AnnotationUtils.createAnnotationSet();
        for (AnnotationExpr annotation : annotations) {
            AnnotationMirror annoMirror = getAnnotation(annotation, supportedAnnotations, processingEnv);
            if (annoMirror != null)
                annos.add(annoMirror);
        }
        String key = ElementUtils.getVerboseName(elt);
        declAnnos.put(key, annos);
    }

    private void annotateParameters(List<? extends AnnotatedTypeMirror> typeArguments,
            List<TypeParameter> typeParameters) {
        if (typeParameters == null)
            return;

        if (typeParameters.size() != typeArguments.size()) {
            System.out.printf("annotateParameters: mismatched sizes%n  typeParameters (size %d)=%s%n  typeArguments (size %d)=%s%n", typeParameters.size(), typeParameters, typeArguments.size(), typeArguments);
        }
        for (int i = 0; i < typeParameters.size(); ++i) {
            TypeParameter param = typeParameters.get(i);
            AnnotatedTypeVariable paramType = (AnnotatedTypeVariable)typeArguments.get(i);

            if (param.getTypeBound() != null && param.getTypeBound().size() == 1) {
                annotate(paramType.getUpperBound(), param.getTypeBound().get(0));
            }
        }
    }

    private static final Set<String> nestedClassWarnings = new HashSet<String>();

    private Map<Element, BodyDeclaration> getMembers(TypeElement typeElt, TypeDeclaration typeDecl) {
        assert (typeElt.getSimpleName().contentEquals(typeDecl.getName())
                || typeDecl.getName().endsWith("$" + typeElt.getSimpleName().toString()))
            : String.format("%s  %s", typeElt.getSimpleName(), typeDecl.getName());

        Map<Element, BodyDeclaration> result = new HashMap<Element, BodyDeclaration>();

        for (BodyDeclaration member : typeDecl.getMembers()) {
            if (member instanceof MethodDeclaration) {
                Element elt = findElement(typeElt, (MethodDeclaration)member);
                putNew(result, elt, member);
            } else if (member instanceof ConstructorDeclaration) {
                Element elt = findElement(typeElt, (ConstructorDeclaration)member);
                putNew(result, elt, member);
            } else if (member instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)member;
                for (VariableDeclarator var : fieldDecl.getVariables()) {
                    putNew(result, findElement(typeElt, var), fieldDecl);
                }
            } else if (member instanceof ClassOrInterfaceDeclaration) {
                // TODO: handle nested classes
                ClassOrInterfaceDeclaration ciDecl = (ClassOrInterfaceDeclaration) member;
                String nestedClass = typeDecl.getName() + "." + ciDecl.getName();
                if (nestedClassWarnings.add(nestedClass)) { // avoid duplicate warnings
                    System.err.printf("Warning: ignoring nested class in %s at line %d:%n    class %s { class %s { ... } }%n", filename, ciDecl.getBeginLine(), typeDecl.getName(), ciDecl.getName());
                    System.err.printf("  Instead, write the nested class as a top-level class:%n    class %s { ... }%n    class %s$%s { ... }%n", typeDecl.getName(), typeDecl.getName(), ciDecl.getName());
                }
            } else {
                if (warnIfNotFound || debugStubParser)
                    System.out.printf("StubParser: Ignoring element of type %s in getMembers", member.getClass());
            }
        }
        // // remove null keys, which can result from findElement returning null
        // result.remove(null);
        return result;
    }

    private AnnotatedDeclaredType findType(ClassOrInterfaceType type, List<AnnotatedDeclaredType> types) {
        String typeString = type.getName();
        for (AnnotatedDeclaredType superType : types) {
            if (superType.getUnderlyingType().asElement().getSimpleName().contentEquals(typeString))
                return superType;
        }
        if (warnIfNotFound || debugStubParser)
            stubWarning("Type " + typeString + " not found");
        if (debugStubParser)
            for (AnnotatedDeclaredType superType : types)
                System.err.printf("  %s%n", superType);
        return null;
    }

    public ExecutableElement findElement(TypeElement typeElt, MethodDeclaration methodDecl) {
        final String wantedMethodName = methodDecl.getName();
        final int wantedMethodParams =
            (methodDecl.getParameters() == null) ? 0 :
                methodDecl.getParameters().size();
        final String wantedMethodString = StubUtil.toString(methodDecl);
        for (ExecutableElement method : ElementUtils.getAllMethodsIn(typeElt)) {
            // do heuristics first
            if (wantedMethodParams == method.getParameters().size()
                && wantedMethodName.contentEquals(method.getSimpleName())
                && StubUtil.toString(method).equals(wantedMethodString)) {
                return method;
            }
        }
        if (warnIfNotFound || debugStubParser)
            stubWarning("Method " + wantedMethodString + " not found in type " + typeElt);
        if (debugStubParser)
            for (ExecutableElement method : ElementFilter.methodsIn(typeElt.getEnclosedElements()))
                System.err.printf("  %s%n", method);
        return null;
    }

    public ExecutableElement findElement(TypeElement typeElt, ConstructorDeclaration methodDecl) {
        final int wantedMethodParams =
            (methodDecl.getParameters() == null) ? 0 :
                methodDecl.getParameters().size();
        final String wantedMethodString = StubUtil.toString(methodDecl);
        for (ExecutableElement method : ElementFilter.constructorsIn(typeElt.getEnclosedElements())) {
            // do heuristics first
            if (wantedMethodParams == method.getParameters().size()
                    && StubUtil.toString(method).equals(wantedMethodString))
                return method;
        }
        if (warnIfNotFound || debugStubParser)
            stubWarning("Constructor " + wantedMethodString + " not found in type " + typeElt);
        if (debugStubParser)
            for (ExecutableElement method : ElementFilter.constructorsIn(typeElt.getEnclosedElements()))
                System.err.printf("  %s%n", method);
        return null;
    }

    public VariableElement findElement(TypeElement typeElt, VariableDeclarator variable) {
        final String fieldName = variable.getId().getName();
        return findFieldElement(typeElt, fieldName);
    }

    public VariableElement findFieldElement(TypeElement typeElt, String fieldName) {
        for (VariableElement field : ElementUtils.getAllFieldsIn(typeElt)) {
            // field.getSimpleName() is a CharSequence, not a String
            if (fieldName.equals(field.getSimpleName().toString())) {
                return field;
            }
        }
        if (warnIfNotFound || debugStubParser)
            stubWarning("Field " + fieldName + " not found in type " + typeElt);
        if (debugStubParser)
            for (VariableElement field : ElementFilter.fieldsIn(typeElt.getEnclosedElements()))
                System.err.printf("  %s%n", field);
        return null;
    }
    
    private TypeElement findType(String typeName, String... msg) {
   	 TypeElement classElement = elements.getTypeElement(typeName);
        if (classElement == null) {
        	if (warnIfNotFound || debugStubParser) {
        		if (msg.length == 0) {
        			stubWarning("Type not found: " + typeName);
        		} else {
        			stubWarning(msg[0] + ": " + typeName);
        		}
        	}
        } 
        return classElement;
	}

    private PackageElement findPackage(String packageName) {
    	PackageElement packageElement = elements.getPackageElement(packageName);
    	if (packageElement == null) {
    		if (warnIfNotFound || debugStubParser) {
    			stubWarning("Imported package not found: " + packageName);
    		}
    	}
    	return packageElement;
    }
    
    private Pair<String, String> partitionQualifiedName(String imported) {
		String typeName = imported.substring(0, imported.lastIndexOf("."));
		String name = imported.substring(imported.lastIndexOf(".") + 1);
		Pair<String,String> typeParts = Pair.of(typeName, name);
		return typeParts;
	}

    /** The line separator */
    private final static String LINE_SEPARATOR = System.getProperty("line.separator").intern();

    /** Just like Map.put, but errs if the key is already in the map. */
    private static <K,V> void putNew(Map<K,V> m, K key, V value) {
        if (key == null)
            return;
        if (m.containsKey(key) && !m.get(key).equals(value)) {
            // TODO: instead of failing, can we try merging the information from
            // multiple stub files?
            ErrorReporter.errorAbort("StubParser: key is already in map: " + LINE_SEPARATOR
                            + "  " + key + " => " + m.get(key) + LINE_SEPARATOR
                            + "while adding: " + LINE_SEPARATOR
                            + "  " + key + " => " + value);
        }
        m.put(key, value);
    }

    /** Just like Map.put, but does not throw an error if the key with the same value is already in the map. */
    private static void putNew(Map<Element, AnnotatedTypeMirror> m, Element key, AnnotatedTypeMirror value) {
        if (key == null)
            return;
        if (m.containsKey(key)) {
            AnnotatedTypeMirror value2 = m.get(key);
            // Are the two values the same?
            if (AnnotationUtils.areSame(value.getAnnotations(), value2.getAnnotations())) {
                return;
            }
            AnnotatedTypeMirror prev = m.get(key);
            mergeATM(value, prev);
        }
        m.put(key, value);
    }

    /**
     * Merge the qualifiers from the second parameter into the first parameter.
     * Modifies the first parameter directly.
     * Raises an exception if both types have a qualifier in a given hierarchy.
     * 
     * @param into target type
     * @param from source type
     */
    // Should we move this to AnnotationUtils? The way collisions are handled is specific.
    private static void mergeATM(AnnotatedTypeMirror into, AnnotatedTypeMirror from) {
        assert into.getClass() == from.getClass();
        // Everybody needs to merge the main qualifier.
        for (AnnotationMirror afrom : from.getAnnotations()) {
            if (into.isAnnotatedInHierarchy(afrom) &&
                    !AnnotationUtils.areSame(into.getAnnotationInHierarchy(afrom), afrom)) {
                // TODO: raise error on the caller, this message might not help in debugging.
                ErrorReporter.errorAbort("StubParser: key is already in map: " + LINE_SEPARATOR
                        + " existing: " + into + " new: " + from);
                return; // dead code
            } else {
                into.addAnnotation(afrom);
            }
        }

        if (from instanceof AnnotatedArrayType) {
            AnnotatedArrayType cinto = (AnnotatedArrayType) into;
            AnnotatedArrayType cfrom = (AnnotatedArrayType) from;
            // Also merge the component types.
            mergeATM(cinto.getComponentType(), cfrom.getComponentType());
        } else if (from instanceof AnnotatedDeclaredType) {
            AnnotatedDeclaredType cinto = (AnnotatedDeclaredType) into;
            AnnotatedDeclaredType cfrom = (AnnotatedDeclaredType) from;
            mergeATMs(cinto.getTypeArguments(), cfrom.getTypeArguments());
        } else if (from instanceof AnnotatedExecutableType) {
            AnnotatedExecutableType cinto = (AnnotatedExecutableType) into;
            AnnotatedExecutableType cfrom = (AnnotatedExecutableType) from;
            mergeATMs(cinto.getTypeVariables(), cinto.getTypeVariables());
            mergeATM(cinto.getReturnType(), cfrom.getReturnType());
            mergeATM(cinto.getReceiverType(), cfrom.getReceiverType());
            mergeATMs(cinto.getParameterTypes(), cfrom.getParameterTypes());
            mergeATMs(cinto.getThrownTypes(), cfrom.getThrownTypes());
        } else if (from instanceof AnnotatedTypeVariable) {
            AnnotatedTypeVariable cinto = (AnnotatedTypeVariable) into;
            AnnotatedTypeVariable cfrom = (AnnotatedTypeVariable) from;
            mergeATM(cinto.getLowerBound(), cfrom.getLowerBound());
            mergeATM(cinto.getUpperBound(), cfrom.getUpperBound());
        } else if (from instanceof AnnotatedWildcardType) {
            AnnotatedWildcardType cinto = (AnnotatedWildcardType) into;
            AnnotatedWildcardType cfrom = (AnnotatedWildcardType) from;
            mergeATM(cinto.getSuperBound(), cfrom.getSuperBound());
            mergeATM(cinto.getExtendsBound(), cfrom.getExtendsBound());
        } else {
            // Remainder: No, Null, Primitive
            // Nothing to do.
        }
    }

    private static void mergeATMs(List<? extends AnnotatedTypeMirror> into, List<? extends AnnotatedTypeMirror> from) {
        assert into.size() == from.size();
        for (int i=0; i<into.size(); ++i) {
            mergeATM(into.get(i), from.get(i));
        }
    }

    /** Just like Map.putAll, but errs if any key is already in the map. */
    private static <K,V> void putAllNew(Map<K,V> m, Map<K,V> m2) {
        for (Map.Entry<K,V> e2 : m2.entrySet()) {
            putNew(m, e2.getKey(), e2.getValue());
        }
    }

    private static Set<String> warnings = new HashSet<String>();

    /** Issues the given warning, only if it has not been previously issued. */
    private static void stubWarning(String warning) {
        if (warnings.add(warning)) {
            System.err.println("StubParser: " + warning);
        }
    }

    private AnnotationMirror getAnnotation(AnnotationExpr annotation,
            Map<String, AnnotationMirror> supportedAnnotations,
            ProcessingEnvironment env) {
        AnnotationMirror annoMirror;
        if (annotation instanceof MarkerAnnotationExpr) {
            String annoName = ((MarkerAnnotationExpr)annotation).getName().getName();
            annoMirror = supportedAnnotations.get(annoName);
        } else if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr nrmanno = (NormalAnnotationExpr)annotation;
            String annoName = nrmanno.getName().getName();
            annoMirror = supportedAnnotations.get(annoName);
            if (annoMirror == null) {
                // Not a supported qualifier -> ignore
                return null;
            }
            AnnotationBuilder builder = new AnnotationBuilder(env, annoMirror);
            List<MemberValuePair> pairs = nrmanno.getPairs();
            if (pairs!=null) {
                for (MemberValuePair mvp : pairs) {
                    String meth = mvp.getName();
                    Expression exp = mvp.getValue();
                    handleExpr(builder, meth, exp);
                }
            }
            return builder.build();
        } else if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr sglanno = (SingleMemberAnnotationExpr)annotation;
            String annoName = sglanno.getName().getName();
            annoMirror = supportedAnnotations.get(annoName);
            if (annoMirror == null) {
                // Not a supported qualifier -> ignore
                return null;
            }
            AnnotationBuilder builder = new AnnotationBuilder(env, annoMirror);
            Expression valexpr = sglanno.getMemberValue();
            handleExpr(builder, "value", valexpr);
            return builder.build();
        } else {
            ErrorReporter.errorAbort("StubParser: unknown annotation type: " + annotation);
            annoMirror = null; // dead code
        }
        return annoMirror;
    }

    // TODO: The only compile time constants supported here are Strings
    private void handleExpr(AnnotationBuilder builder, String name,
            Expression expr) {
        if (expr instanceof FieldAccessExpr || expr instanceof NameExpr) {
            VariableElement elem;
            if (expr instanceof FieldAccessExpr) {
                elem = findVariableElement((FieldAccessExpr)expr);
            } else {
                elem = findVariableElement((NameExpr)expr);
            }

            if (elem == null) {
                // A warning was already issued by findVariableElement;
                return;
            }

            ExecutableElement var = builder.findElement(name);
            TypeMirror expected = var.getReturnType();
            if (expected.getKind() == TypeKind.DECLARED) {
                if (elem.getConstantValue()!=null) {
                    builder.setValue(name, (String) elem.getConstantValue());
                } else {
                    builder.setValue(name, elem);
                }
            } else if (expected.getKind() == TypeKind.ARRAY) {
                if (elem.getConstantValue()!=null) {
                    String[] arr = { (String) elem.getConstantValue() };
                    builder.setValue(name, arr);
                } else {
                    VariableElement[] arr = { elem };
                    builder.setValue(name, arr);
                }
            } else {
<<<<<<< variant A
                SourceChecker.errorAbort("StubParser: unhandled annotation attribute type: " + expr + " and expected: " + expected);
>>>>>>> variant B
                ErrorReporter.errorAbort("StubParser: unhandled annotation attribute type: " + faexpr + " and expected: " + expected);
####### Ancestor
                SourceChecker.errorAbort("StubParser: unhandled annotation attribute type: " + faexpr + " and expected: " + expected);
======= end
            }
        } else if (expr instanceof StringLiteralExpr) {
            StringLiteralExpr slexpr = (StringLiteralExpr) expr;
            ExecutableElement var = builder.findElement(name);
            TypeMirror expected = var.getReturnType();
            if (expected.getKind() == TypeKind.DECLARED) {
                builder.setValue(name, slexpr.getValue());
            } else if (expected.getKind() == TypeKind.ARRAY) {
                String[] arr = { slexpr.getValue() };
                builder.setValue(name, arr);
            } else {
                ErrorReporter.errorAbort("StubParser: unhandled annotation attribute type: " + slexpr + " and expected: " + expected);
            }
        } else if (expr instanceof ArrayInitializerExpr) {
            ExecutableElement var = builder.findElement(name);
            TypeMirror expected = var.getReturnType();
            if (expected.getKind() != TypeKind.ARRAY) {
                ErrorReporter.errorAbort("StubParser: unhandled annotation attribute type: " + expr + " and expected: " + expected);
            }

            ArrayInitializerExpr aiexpr = (ArrayInitializerExpr) expr;
            List<Expression> aiexprvals = aiexpr.getValues();

            Object[] elemarr = new Object[aiexprvals.size()];

            Expression anaiexpr;
            for (int i = 0; i < aiexprvals.size(); ++i) {
                anaiexpr = aiexprvals.get(i);
                if (anaiexpr instanceof FieldAccessExpr
                        || anaiexpr instanceof NameExpr) {
                    
                    if (anaiexpr instanceof FieldAccessExpr) {
                        elemarr[i] = findVariableElement((FieldAccessExpr) anaiexpr);
                    } else {
                        elemarr[i] = findVariableElement((NameExpr) anaiexpr);
                    }
                    
                    if (elemarr[i] == null) {
                        // A warning was already issued by findVariableElement;
                        return;
                    }
                    String constval = (String) ((VariableElement)elemarr[i]).getConstantValue();
                    if (constval!=null) {
                        elemarr[i] = constval;
                    }
                } else if (anaiexpr instanceof StringLiteralExpr) {
                    elemarr[i] = ((StringLiteralExpr) anaiexpr).getValue();
                } else {
                    ErrorReporter.errorAbort("StubParser: unhandled annotation attribute type: " + anaiexpr);
                }
            }

            builder.setValue(name, elemarr);
        } else {
            ErrorReporter.errorAbort("StubParser: unhandled annotation attribute type: " + expr + " class: " + expr.getClass());
        }
    }
    
    private /*@Nullable*/ VariableElement findVariableElement(NameExpr nexpr) {
        if (nexprcache.containsKey(nexpr)) {
            return nexprcache.get(nexpr);
        }
        
        VariableElement res = null;
        boolean importFound = false;
        for (String imp: imports) {
            Pair<String, String> partitionedName = partitionQualifiedName(imp);
            String typeName = partitionedName.first;
            String fieldName = partitionedName.second;
            if (fieldName.equals(nexpr.getName())) {
                TypeElement enclType = findType(typeName, 
                		String.format("Enclosing type of static import %s not found", fieldName));
                
                if (enclType == null) {
                    return null;
                } else {
                	importFound = true;
                	res = findFieldElement(enclType, fieldName);
                	break;
                }
            }
        }

        // Imported but invalid types or fields will have warnings from above,
        // only warn on fields missing an import
        if (res == null && !importFound) {
            if (warnIfNotFound || debugStubParser) {
                stubWarning("Static field " + nexpr.getName() + " is not imported");
            }
        }
        
        nexprcache.put(nexpr, res);
        return res;
    }
    
    private /*@Nullable*/ VariableElement findVariableElement(FieldAccessExpr faexpr) {
        if (faexprcache.containsKey(faexpr)) {
            return faexprcache.get(faexpr);
        }
        TypeElement rcvElt = elements.getTypeElement(faexpr.getScope().toString());
        if (rcvElt == null) {
            // Search imports for full annotation name.
            for (String imp: imports) {
                String[] import_delimited = imp.split("\\.");
                if (import_delimited[import_delimited.length - 1].equals(faexpr.getScope().toString())) {
                    StringBuilder full_annotation = new StringBuilder();
                    for (int i = 0; i < import_delimited.length - 1; i++) {
                        full_annotation.append(import_delimited[i]);
                        full_annotation.append('.');
                    }
                    full_annotation.append(faexpr.getScope().toString());
                    rcvElt = elements.getTypeElement(full_annotation);
                    break;
                }
            }

            if (rcvElt == null) {
                if (warnIfNotFound || debugStubParser)
                    stubWarning("Type " + faexpr.getScope().toString() + " not found");
                return null;
            }
        }

        VariableElement res = findFieldElement(rcvElt, faexpr.getField());
        faexprcache.put(faexpr, res);
        return res;
    }
}
