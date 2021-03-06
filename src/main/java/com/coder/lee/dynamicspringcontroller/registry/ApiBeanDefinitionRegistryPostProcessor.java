package com.coder.lee.dynamicspringcontroller.registry;

import com.coder.lee.dynamicspringcontroller.interceptor.ControllerInterceptor;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Description: ApiBeanDefinitionRegistryPostProcessor
 * Copyright: Copyright (c)
 * Company: Ruijie Co., Ltd.
 * Create Time: 2021/4/20 1:33
 *
 * @author coderLee23
 */
@Component
public class ApiBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiBeanDefinitionRegistryPostProcessor.class);

    private static final ClassLoader CLASS_LOADER = ApiBeanDefinitionRegistryPostProcessor.class.getClassLoader();

    // ??????BeanDefinitionRegistryPostProcessor???????????????@Value??????(spring?????????????????????)?????????????????????
    //private static final String BASE_PATH = "com.coder.lee.dynamicspringcontroller.service.test";
    private static final String API_SCAN_PATH = "api.scan.path";

    private static final String DEFAULT_PROPERTIES = "application.properties";

    private static final String IMPL_SUFFIX = "Impl";

    private static final String API_IMPL_SUFFIX = "API" + IMPL_SUFFIX;

    private static final String ENDPOINT_PREFIX = "org.springframework.bytebuddy.endpoint.";

    private static final String CONTROLLER_SUFFIX = "Controller";

    private static final String VALUE = "value";

    private static final String SAVE_FILE_PATH = String.format("%s\\src\\test\\java", System.getProperty("user.dir"));

    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // ???????????????api?????????bean???interface???API?????????????????????APIImpl??????
        // ??????API???????????????spring???????????????????????????????????????
        Set<BeanDefinition> scanList = getApiBeanDefinitions();
        // ??????XXXAPIImpl???spring???
        registryBeanDefinitions(registry, scanList);
        // ??????controller???spring???
        makeAndRegistryControllerBeanDefinitions(registry, scanList);
    }

    private Properties getProperties() {
        Properties props = new Properties();
        //??????Properies??????????????????
        try {
            props.load(CLASS_LOADER.getResourceAsStream(DEFAULT_PROPERTIES));
        } catch (IOException e) {
            LOGGER.error("?????????????????????????????????", e);
        }
        return props;
    }

    private void makeAndRegistryControllerBeanDefinitions(BeanDefinitionRegistry registry, Set<BeanDefinition> scanList) {
        for (BeanDefinition beanDefinition : scanList) {
            final String beanClassName = getBeanSimpleCLassName(beanDefinition);
            //??????????????????API interface??????
            String interfaceClass = getInterfaceClass(beanDefinition.getBeanClassName());
            LOGGER.info("??????interfaceClass {}", interfaceClass);
            if (StringUtils.hasText(interfaceClass)) {
                Class<?> beanClazz = makeController(beanClassName, interfaceClass);
                //????????????controller?????????spring?????????
                registerControllerBeanDefinition(registry, beanClazz);
            }
        }
    }

    private Class<?> makeController(String beanClassName, String interfaceClass) {
        // ??????API?????????class
        Class<?> interfaceClazz = getInterfaceClazz(interfaceClass);
        Assert.notNull(interfaceClazz, "interfaceClazz must not be null");
        // ??????????????????spring???????????????bean??????????????????????????????????????????controller???
        Object bean = applicationContext.getBean(beanClassName);
        // ??????controller
        DynamicType.Builder<Object> controllerBuilder = builderController(bean);
        // ??????controller??????method
        DynamicType.Unloaded<?> dynamicType = getObjectUnloaded(interfaceClazz, bean, controllerBuilder);
        // ???????????????controller???class??????????????????????????????????????????
        saveIn(dynamicType);
        return dynamicType.load(CLASS_LOADER, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
    }

    private DynamicType.Unloaded<?> getObjectUnloaded(Class<?> interfaceClazz, Object bean, DynamicType.Builder<Object> controllerBuilder) {
        // ??????controller invocation API ??????
        InvocationHandler invocationHandler = new ControllerInterceptor(bean);
        // ??????controller???methods
        Method[] declaredMethods = interfaceClazz.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {
            String name = declaredMethod.getName();
            String methodName = name.substring(name.lastIndexOf(".") + 1);
            Parameter[] parameters = declaredMethod.getParameters();
            String postMappingName = getPostMappingName(methodName, parameters);
            // @PostMapping(value="xxx")
            AnnotationDescription postMapping = getPostMappingAnnotationDescription(postMappingName);
            AnnotationDescription apiOperationDescription = getApiOperationDescription(postMappingName);
            DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial<Object> objectInitial = controllerBuilder.defineMethod(methodName, declaredMethod.getReturnType(), Modifier.PUBLIC);
            if (ArrayUtils.isNotEmpty(parameters)) {
                // method ????????????
                DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable annotatable = null;
                for (Parameter parameter : parameters) {
                    annotatable = objectInitial.withParameter(parameter.getType(), parameter.getName()).annotateParameter(getRequestParamAnnotationDescription(), getValidatedDescription());
                }
                controllerBuilder = annotatable.throwing(Throwable.class).intercept(InvocationHandlerAdapter.of(invocationHandler)).annotateMethod(postMapping, apiOperationDescription);
            } else {
                //????????????
                controllerBuilder = objectInitial.throwing(Throwable.class).intercept(InvocationHandlerAdapter.of(invocationHandler)).annotateMethod(postMapping, apiOperationDescription);
            }
        }
        return controllerBuilder.make();
    }

    private String getPostMappingName(String methodName, Parameter[] parameters) {
        // ????????????????????????????????????PostMapping value????????????????????????????????????????????????????????????
        return Arrays.stream(parameters).map(parameter -> parameter.getType().getSimpleName()).collect(Collectors.joining("_", methodName + "_", ""));
    }

    private AnnotationDescription getPostMappingAnnotationDescription(String name) {
        return AnnotationDescription.Builder.ofType(PostMapping.class)
                .defineArray(VALUE, name)
                .defineArray("produces", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private AnnotationDescription getRequestParamAnnotationDescription() {
        return AnnotationDescription.Builder.ofType(RequestBody.class)
                .build();
    }

    private AnnotationDescription getValidatedDescription() {
        return AnnotationDescription.Builder.ofType(Validated.class)
                .build();
    }


    private DynamicType.Builder<Object> builderController(Object bean) {
        // @Api(tags="xxx")
        AnnotationDescription apiOperationDesc = getApiAnnotationDescription();
        // @RestController("/")
        AnnotationDescription restControllerDesc = getRestControllerAnnotationDescription();
        return new ByteBuddy()
                // ????????????
                .subclass(Object.class)
                // ??????controller??????
                .name(ENDPOINT_PREFIX.concat(bean.getClass().getSimpleName()).concat(CONTROLLER_SUFFIX))
                // ?????? @Controller, @Api ??????
                .annotateType(restControllerDesc, apiOperationDesc);
    }

    private AnnotationDescription getRestControllerAnnotationDescription() {
        return AnnotationDescription.Builder.ofType(RestController.class)
                .define(VALUE, "/")
                .build();
    }

    private AnnotationDescription getApiAnnotationDescription() {
        return AnnotationDescription.Builder.ofType(Api.class).build();
    }

    private AnnotationDescription getApiOperationDescription(String postMappingName) {
        return AnnotationDescription.Builder.ofType(ApiOperation.class)
                .define(VALUE, postMappingName)
                .build();
    }

    private Class<?> getInterfaceClazz(String interfaceClass) {
        try {
            return CLASS_LOADER.loadClass(interfaceClass);
        } catch (ClassNotFoundException e) {
            LOGGER.error("ClassNotFoundException error ", e);
        }
        return null;
    }

    private void saveIn(DynamicType.Unloaded<?> dynamicType) {
        try {
            dynamicType.saveIn(new File(SAVE_FILE_PATH));
        } catch (IOException e) {
            LOGGER.error("saveIn error ", e);
        }
    }

    private void registerControllerBeanDefinition(BeanDefinitionRegistry registry, Class<?> beanClazz) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClazz);
        GenericBeanDefinition definition = (GenericBeanDefinition) beanDefinitionBuilder.getRawBeanDefinition();
        String simpleName = beanClazz.getSimpleName();
        // ???controller?????????
        registry.registerBeanDefinition(simpleName, definition);
        LOGGER.info("register controller {} bean definition: {}", simpleName, definition);
    }

    private void registryBeanDefinitions(BeanDefinitionRegistry registry, Set<BeanDefinition> scanList) {
        for (BeanDefinition beanDefinition : scanList) {
            final String beanClassName = getBeanSimpleCLassName(beanDefinition);
            registry.registerBeanDefinition(beanClassName, beanDefinition);
            LOGGER.info("??????beanName??????{}??????bean???{}??????spring?????????", beanClassName, beanDefinition.getBeanClassName());
        }
    }

    private Set<BeanDefinition> getApiBeanDefinitions() {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(
                false);
        provider.addIncludeFilter((metadataReader, metadataReaderFactory) -> metadataReader.getClassMetadata().getClassName().endsWith(API_IMPL_SUFFIX));
        Properties properties = getProperties();
        String apiPath = properties.getProperty(API_SCAN_PATH);
        Assert.hasText(apiPath, String.format("%s must be configured in %s", API_SCAN_PATH, DEFAULT_PROPERTIES));
        return provider.findCandidateComponents(apiPath);
    }

    private String getInterfaceClass(String beanClassName) {
        MetadataReaderFactory factory = new SimpleMetadataReaderFactory();
        try {
            MetadataReader metadataReader = factory.getMetadataReader(beanClassName);
            String[] interfaceNames = metadataReader.getClassMetadata().getInterfaceNames();
            String beanClassNameSuffix = beanClassName.substring(beanClassName.lastIndexOf(".") + 1);
            return Arrays.stream(interfaceNames).filter(interfaceName -> interfaceName.substring(interfaceName.lastIndexOf(".") + 1).concat(IMPL_SUFFIX).equals(beanClassNameSuffix)).findFirst().orElse(null);
        } catch (IOException e) {
            LOGGER.error("??????interface class error", e);
        }
        return null;
    }

    private String getBeanSimpleCLassName(BeanDefinition beanDefinition) {
        String beanClassName = beanDefinition.getBeanClassName();
        Assert.hasText(beanClassName, "beanClassName must not be null or empty");
        beanClassName = beanClassName.substring(beanClassName.lastIndexOf(".") + 1);
        return beanClassName;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


}
