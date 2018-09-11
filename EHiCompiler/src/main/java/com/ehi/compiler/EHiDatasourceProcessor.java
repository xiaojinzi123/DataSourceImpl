package com.ehi.compiler;

import com.ehi.api.EHiDataSourceAnno;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * 实现项目中的 retrofit 接口的代理接口和实现类生成,生成的类作为 datasource 层
 * time   : 2018/08/10
 *
 * @author : xiaojinzi 30212
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({"com.ehi.api.EHiDataSourceAnno"})
public class EHiDatasourceProcessor extends AbstractProcessor {

    private TypeMirror typeString;
    private TypeMirror typeVoid;

    private Filer mFiler;
    private Messager mMessager;
    private Types mTypes;
    private Elements mElements;

    private String classNameApi = "com.ehi.datasource.DataSourceApi";
    private String classNameApiImpl = "com.ehi.datasource.DataSourceApiImpl";
    private String classNameApiManager = "com.ehi.datasource.DataSourceManager";
    ;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        mFiler = processingEnv.getFiler();
        mMessager = processingEnvironment.getMessager();
        mTypes = processingEnv.getTypeUtils();
        mElements = processingEnv.getElementUtils();

        typeString = mElements.getTypeElement("java.lang.String").asType();
        typeVoid = mElements.getTypeElement("java.lang.Void").asType();

    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        if (CollectionUtils.isNotEmpty(set)) {

            Set<? extends Element> routeElements = roundEnvironment.getElementsAnnotatedWith(EHiDataSourceAnno.class);

            parseAnno(routeElements);

            return true;
        }

        return false;

    }

    /**
     * 解析注解
     *
     * @param currElements
     */
    private void parseAnno(Set<? extends Element> currElements) {

        List<TypeElement> list = new ArrayList<>();

        Set<String> prefixSet = new HashSet<>();
        Set<String> uniqueSet = new HashSet<>();

        for (Element element : currElements) {

            TypeMirror tm = element.asType();

            if (!(element instanceof TypeElement)) {

                mMessager.printMessage(Diagnostic.Kind.ERROR, element + " is not a 'TypeElement' ");

                continue;

            }

            EHiDataSourceAnno anno = element.getAnnotation(EHiDataSourceAnno.class);

            if (anno.impl().isEmpty() && anno.callPath().isEmpty()) {

                mMessager.printMessage(Diagnostic.Kind.ERROR, element.toString() + ": EHiDataSourceAnno's impl and EHiDataSourceAnno's callPath are both empty");

                continue;
            }

            if (!anno.value().isEmpty()) {

                if (prefixSet.contains(anno.value())) {
                    mMessager.printMessage(Diagnostic.Kind.ERROR, element.toString() + ": EHiDataSourceAnno's value is already exist");
                    continue;
                }
                prefixSet.add(anno.value());

            }

            if (uniqueSet.contains(anno.uniqueCode())) {

                mMessager.printMessage(Diagnostic.Kind.ERROR, element.toString() + ": EHiDataSourceAnno's uniqueCode is not unique");

                continue;
            }

            uniqueSet.add(anno.uniqueCode());

            list.add((TypeElement) element);

        }

        try {
            createDataSourceApi(list);
            createDataSourceApiManager(list);
            createDataSourceApiImpl(list);
        } catch (Exception e) {
            mMessager.printMessage(Diagnostic.Kind.ERROR, "createDataSource fail: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void createDataSourceApi(List<TypeElement> apiClassList) throws IOException {

        // pkg
        String pkgApi = classNameApi.substring(0, classNameApi.lastIndexOf("."));

        // simpleName
        String cnApi = classNameApi.substring(classNameApi.lastIndexOf(".") + 1);

        TypeSpec.Builder typeSpecBuilder = TypeSpec.interfaceBuilder(cnApi)
                .addModifiers(Modifier.PUBLIC);

        typeSpecBuilder.addJavadoc("所有用注解标记的DataSource都会被整合到这里\n\n");

        for (TypeElement typeElement : apiClassList) {

            EHiDataSourceAnno remoteAnno = typeElement.getAnnotation(EHiDataSourceAnno.class);

            if (remoteAnno == null) {
                continue;
            }

            typeSpecBuilder.addJavadoc("@see " + typeElement.toString() + "\n");
            generateMethods(typeElement.getEnclosedElements(), typeSpecBuilder, remoteAnno, false);

        }

        TypeSpec typeSpec = typeSpecBuilder.build();

        JavaFile.builder(pkgApi, typeSpec)
                .indent("    ")
                .build()
                .writeTo(mFiler);

    }

    private void createDataSourceApiManager(List<TypeElement> apiClassList) throws IOException {

        // pkg
        String pkgApi = classNameApiManager.substring(0, classNameApiManager.lastIndexOf("."));

        // simpleName
        String cnApi = classNameApiManager.substring(classNameApiManager.lastIndexOf(".") + 1);

        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(cnApi)
                .addModifiers(Modifier.PUBLIC);

        ClassName mapClassName = ClassName.get("java.util", "Map");

        TypeName typeNameMap = ParameterizedTypeName.get(mapClassName, TypeName.get(typeString), TypeName.get(mElements.getTypeElement("java.lang.Object").asType()));

        FieldSpec.Builder mapFieldSpecBuilder = FieldSpec.builder(typeNameMap, "map", Modifier.PRIVATE)
                .initializer("java.util.Collections.synchronizedMap(new java.util.HashMap<String,Object>())");

        typeSpecBuilder
                .addField(mapFieldSpecBuilder.build());

        for (TypeElement typeElement : apiClassList) {

            EHiDataSourceAnno anno = typeElement.getAnnotation(EHiDataSourceAnno.class);

            if (anno == null || anno.impl().isEmpty()) {
                continue;
            }

            TypeMirror returnType = typeElement.asType();

            MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(anno.uniqueCode() + "DataSource")
                    .returns(TypeName.get(returnType));

            String implClassName = anno.impl();

            methodSpecBuilder
                    .addStatement(implClassName + " value = (" + implClassName + ") map.get(\"" + anno.uniqueCode() + "\");")
                    .beginControlFlow("if (value == null)")
                    .addStatement("value = " + " new " + implClassName + "()")
                    .addStatement("map.put(\"" + anno.uniqueCode() + "\", value);")
                    .endControlFlow()
                    .addStatement("return value")
                    .addModifiers(Modifier.PUBLIC);

            MethodSpec methodSpec = methodSpecBuilder.build();

            typeSpecBuilder.addMethod(methodSpec);


        }


        // 内部的Holder 静态类
        TypeSpec.Builder typeSpecHolderBuilder = TypeSpec.classBuilder("Holder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        typeSpecHolderBuilder.addField(
                FieldSpec.builder(
                        ClassName.get(classNameApiManager.substring(0, classNameApiManager.lastIndexOf(".")), classNameApiManager.substring(classNameApiManager.lastIndexOf(".") + 1)), "instance"
                )
                        .initializer("new " + classNameApiManager + "()")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .build()
        );

        TypeSpec apiTypeSpec = typeSpecBuilder
                // 添加一个私有的构造函数
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .addType(typeSpecHolderBuilder.build())
                .build();

        JavaFile.builder(pkgApi, apiTypeSpec)
                .indent("    ")
                .build()
                .writeTo(mFiler);

    }

    private void createDataSourceApiImpl(List<TypeElement> apiClassList) throws IOException {

        // pkg
        String pkg = classNameApiImpl.substring(0, classNameApiImpl.lastIndexOf("."));

        // simpleName
        String cn = classNameApiImpl.substring(classNameApiImpl.lastIndexOf(".") + 1);

        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(cn)
                .addModifiers(Modifier.PUBLIC);

        for (TypeElement typeElement : apiClassList) {

            EHiDataSourceAnno remoteAnno = typeElement.getAnnotation(EHiDataSourceAnno.class);

            if (remoteAnno == null) {
                continue;
            }

            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            generateMethods(enclosedElements, typeSpecBuilder, remoteAnno, true);

        }

        ClassName superInterface = ClassName.get(classNameApi.substring(0, classNameApi.lastIndexOf(".")), classNameApi.substring(classNameApi.lastIndexOf(".") + 1));

        typeSpecBuilder.addSuperinterface(superInterface);

        TypeSpec typeSpec = typeSpecBuilder.build();

        JavaFile.builder(pkg, typeSpec)
                .indent("    ")
                .build()
                .writeTo(mFiler);

    }

    private void generateMethods(List<? extends Element> enclosedElements, TypeSpec.Builder typeSpecBuilder, EHiDataSourceAnno anno, boolean isAddStatement) {

        for (Element elementItem : enclosedElements) {

            if (!(elementItem instanceof ExecutableElement)) {
                continue;
            }

            // 可执行的方法
            ExecutableElement executableElement = (ExecutableElement) elementItem;

            String methodName = "";

            if (anno.value().isEmpty()) {
                methodName = executableElement.getSimpleName().toString();
            } else {
                methodName = anno.value() + firstCharToUp(executableElement.getSimpleName().toString());
            }

            MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(methodName);

            String parameterStr = generateParameters(executableElement, methodSpecBuilder);

            methodSpecBuilder.returns(TypeName.get(executableElement.getReturnType()));

            if (isAddStatement) {

                methodSpecBuilder.addModifiers(Modifier.PUBLIC);

                String returnStr = null;

                if (anno.callPath().isEmpty()) {
                    returnStr = classNameApiManager + ".Holder.instance." + anno.uniqueCode() + "DataSource()." + executableElement.getSimpleName().toString() + "(" + parameterStr + ")";
                } else {
                    returnStr = anno.callPath() + "." + executableElement.getSimpleName().toString() + "(" + parameterStr + ")";
                }

                if ("void".equals(executableElement.getReturnType().toString())) {
                    methodSpecBuilder.addStatement(returnStr);
                } else {
                    methodSpecBuilder.addStatement("return " + returnStr);
                }


            } else {
                methodSpecBuilder
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
            }

            MethodSpec methodSpec = methodSpecBuilder.build();

            typeSpecBuilder.addMethod(methodSpec);

        }

    }

    private String generateParameters(ExecutableElement executableElement, MethodSpec.Builder methodSpecBuilder) {

        List<? extends VariableElement> typeParameters = executableElement.getParameters();

        StringBuffer sb = new StringBuffer();

        for (VariableElement typeParameter : typeParameters) {

            //mMessager.printMessage(Diagnostic.Kind.NOTE, "typeParameter ====== " + typeParameter.asType().toString());

            //TypeName.get(mElements.getTypeElement(""))

            TypeName typeName = TypeName.get(typeParameter.asType());

            String parameterName = typeParameter.getSimpleName().toString();

            ParameterSpec parameterSpec = ParameterSpec.builder(typeName, parameterName)
                    .build();

            if (sb.length() == 0) {
                sb.append(parameterName);
            } else {
                sb.append(",").append(parameterName);
            }

            methodSpecBuilder.addParameter(parameterSpec);

        }

        return sb.toString();

    }

    private String firstCharToUp(String str) {
        if (str == null || str.length() == 0) {
            return "";
        }

        String str1 = str.substring(0, 1).toUpperCase();
        String str2 = str.substring(1);

        return str1 + str2;

    }

}
