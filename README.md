#  Java神器byte-buddy动态生成Spring-MVC框架的Controller



## 一、前言

1、问题：

​		针对对于API接口的测试问题，由于API层不能直接通过Swagger注解来生成API文档和快速的进行接口测试。

2、思考：

​		是否针对API接口，根据API接口动态生成controller层，将controller注入到spring中，模拟常规的web方式，实现swagger文档的生成，提供swagger测试能力。

3、实现方式几种思路：

​		（1）可参考Lombok方式，在编译时期生成controller层。

```
Lombok实现原理
1. javac对源代码进行分析，生成一棵抽象语法树(AST)
2. javac编译过程中调用实现了JSR 269的Lombok程序
3. 此时Lombok就对第一步骤得到的AST进行处理，找到Lombok注解所在类对应的语法树(AST)，然后修改该语法树(AST)，增加Lombok注解定义的相应树节点
4. javac使用修改后的抽象语法树(AST)生成字节码文件
```

​      （2）可使用模板引擎方式，类似mybatis-generator生成对应的类和文件。

​      （3）使用字节码增强方式，动态构建controller类，将controller加入spring的bean管理中，使用swagger注解，来生成swagger接口文档，以及提供接口测试能力。

这里选择使用第三种方式来探索实现的可行性。



## 二、开发环境

1. JDK 1.8.131

2. lombok 

3.  knife4j + swagger3

4. byte-buddy 1.10.22

5. springboot

6. spring-boot-starter-actuator

7. spring-boot-starter-validation

   以上为项目的核心包

   引入byte-buddy理由：
   
   ```
   Byte Buddy 是一个代码生成和操作库，用于在 Java 应用程序运行时创建和修改 Java 类，而无需编译器的帮助。除了 Java 类库附带的代码生成实用程序外，Byte Buddy 还允许创建任意类，并且不限于实现用于创建运行时代理的接口。此外，Byte Buddy 提供了一种方便的 API，可以使用 Java 代理或在构建过程中手动更改类。
   
   无需理解字节码指令，即可使用简单的 API 就能很容易操作字节码，控制类和方法。已支持Java 11，库轻量，仅取决于Java字节代码解析器库ASM的访问者API，它本身不需要任何其他依赖项。比起JDK动态代理、cglib、Javassist，Byte Buddy在性能上具有一定的优势。
   ```
   
   

## 三、案例目标

1、动态注册Bean到Spring容器，可实现 BeanDefinitionRegistryPostProcessor接口

2、扫描指定的包路径，以APIImpl为结尾的实现类。(此处假设APIImpl并未使用spring注解)

3、将APIImpl结尾的实现类注册到spring中

4、查找是否存在API interface接口，如果存在，则根据接口构建APIImpl的Controller层，使用动态代理，调用执行APIImpl的方法。

5、使用Swagger注解controller层，生成可以用于测试的接口文档和可测试界面。



## 四、技术实现

1、实现BeanDefinitionRegistryPostProcessor接口，实现postProcessBeanDefinitionRegistry方法

```java
@Override
public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
    // 扫描项目中api定义的bean，interface以API结尾，实现类以APIImpl结尾
    Set<BeanDefinition> scanList = getApiBeanDefinitions();
    // 注册XXXAPIImpl到spring中
    registryBeanDefinitions(registry, scanList);
    // 注册controller到spring中
    makeAndRegistryControllerBeanDefinitions(registry, scanList);
}
```

2、在properties中配置API_SCAN_PATH，用于扫描指定路径的APIImpl实现类，由于spring在BeanDefinitionRegistryPostProcessor注册流程中，不能直接使用@value注解，这里自己实现Properties方式的读取。读取到所有APIImpl实现的BeanDefinition。

```java
private Set<BeanDefinition> getApiBeanDefinitions() {
    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(
            false);
    provider.addIncludeFilter((metadataReader, metadataReaderFactory) -> metadataReader.getClassMetadata().getClassName().endsWith(API_IMPL_SUFFIX));
    Properties properties = getProperties();
    String apiPath = properties.getProperty(API_SCAN_PATH);
    Assert.hasText(apiPath, String.format("%s must be configured in %s", API_SCAN_PATH, DEFAULT_PROPERTIES));
    return provider.findCandidateComponents(apiPath);
}
```

3、扫描到所有的APIImpl实现的BeanDefinition,注册到spring中

```java
private void registryBeanDefinitions(BeanDefinitionRegistry registry, Set<BeanDefinition> scanList) {
    for (BeanDefinition beanDefinition : scanList) {
        final String beanClassName = getBeanSimpleCLassName(beanDefinition);
        registry.registerBeanDefinition(beanClassName, beanDefinition);
        LOGGER.info("注册beanName为【{}】的bean【{}】到spring容器中", beanClassName, beanDefinition.getBeanClassName());
    }
}
```

4、根据APIImpl查找到对应的API interface类，根据API类中的接口，生成Controller层和对应的方式实现。通过byteBuddy方式对controller层的方法代理实现，在代理方法中，去查找对应的APIImpl，并执行真的的APIImpl该方法实现。

```java
private void makeAndRegistryControllerBeanDefinitions(BeanDefinitionRegistry registry, Set<BeanDefinition> scanList) {
    for (BeanDefinition beanDefinition : scanList) {
        final String beanClassName = getBeanSimpleCLassName(beanDefinition);
        //查找是否存在API interface接口
        String interfaceClass = getInterfaceClass(beanDefinition.getBeanClassName());
        LOGGER.info("获取interfaceClass {}", interfaceClass);
        if (StringUtils.hasText(interfaceClass)) {
            Class<?> beanClazz = makeController(beanClassName, interfaceClass);
            //将生成的controller注册到spring容器中
            registerControllerBeanDefinition(registry, beanClazz);
        }
    }
}
```

4-1、查找APIImpl实现类对应的接口

```java
private String getInterfaceClass(String beanClassName) {
    MetadataReaderFactory factory = new SimpleMetadataReaderFactory();
    try {
        MetadataReader metadataReader = factory.getMetadataReader(beanClassName);
        String[] interfaceNames = metadataReader.getClassMetadata().getInterfaceNames();
        String beanClassNameSuffix = beanClassName.substring(beanClassName.lastIndexOf(".") + 1);
        return Arrays.stream(interfaceNames).filter(interfaceName -> interfaceName.substring(interfaceName.lastIndexOf(".") + 1).concat(IMPL_SUFFIX).equals(beanClassNameSuffix)).findFirst().orElse(null);
    } catch (IOException e) {
        LOGGER.error("获取interface class error", e);
    }
    return null;
}
```

4-2、根据接口创建controller层

```java
private Class<?> makeController(String beanClassName, String interfaceClass) {
    // 获取API接口的class
    Class<?> interfaceClazz = getInterfaceClazz(interfaceClass);
    Assert.notNull(interfaceClazz, "interfaceClazz must not be null");
    // 从刚才注册的spring容器中获取bean实例，获取接口类的信息，构造controller层
    Object bean = applicationContext.getBean(beanClassName);
    // 构架controller
    DynamicType.Builder<Object> controllerBuilder = builderController(bean);
    // 构建controller中的method
    DynamicType.Unloaded<?> dynamicType = getObjectUnloaded(interfaceClazz, bean, controllerBuilder);
    // 打印生成的controller的class类文件，便于观察是否生成正常
    saveIn(dynamicType);
    return dynamicType.load(CLASS_LOADER, ClassLoadingStrategy.Default.INJECTION)
            .getLoaded();
}

    private DynamicType.Builder<Object> builderController(Object bean) {
        // @Api(tags="xxx")
        AnnotationDescription apiOperationDesc = getApiAnnotationDescription();
        // @RestController("/")
        AnnotationDescription restControllerDesc = getRestControllerAnnotationDescription();
        return new ByteBuddy()
                // 继承父类
                .subclass(Object.class)
                // 指定controller类名
                .name(ENDPOINT_PREFIX.concat(bean.getClass().getSimpleName()).concat(CONTROLLER_SUFFIX))
                // 添加 @Controller, @Api 注解
                .annotateType(restControllerDesc, apiOperationDesc);
    }

```

动态构建controller中的方法

```java
private DynamicType.Unloaded<?> getObjectUnloaded(Class<?> interfaceClazz, Object bean, DynamicType.Builder<Object> controllerBuilder) {
    // 构造controller invocation API 实现
    InvocationHandler invocationHandler = new ControllerInterceptor(bean);
    // 构建controller的methods
    Method[] declaredMethods = interfaceClazz.getDeclaredMethods();
    for (Method declaredMethod : declaredMethods) {
        String name = declaredMethod.getName();
        String methodName = name.substring(name.lastIndexOf(".") + 1);
        Parameter[] parameters = declaredMethod.getParameters();
        String postMappingName = getPostMappingName(methodName, parameters);
        // @PostMapping(value="xxx")
        AnnotationDescription postMapping = getPostMappingAnnotationDescription(postMappingName);
        // @ApiOperation(value="postMappingName")
        AnnotationDescription apiOperationDescription = getApiOperationDescription(postMappingName);
        DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial<Object> objectInitial = controllerBuilder.defineMethod(methodName, declaredMethod.getReturnType(), Modifier.PUBLIC);
        if (ArrayUtils.isNotEmpty(parameters)) {
            // method 有参处理
            DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable annotatable = null;
            for (Parameter parameter : parameters) {
                annotatable = objectInitial.withParameter(parameter.getType(), parameter.getName()).annotateParameter(getRequestParamAnnotationDescription(), getValidatedDescription());
            }
            controllerBuilder = annotatable.throwing(Throwable.class).intercept(InvocationHandlerAdapter.of(invocationHandler)).annotateMethod(postMapping, apiOperationDescription);
        } else {
            //无参处理
            controllerBuilder = objectInitial.throwing(Throwable.class).intercept(InvocationHandlerAdapter.of(invocationHandler)).annotateMethod(postMapping, apiOperationDescription);
        }
    }
    return controllerBuilder.make();
}
```

4-3  使用动态代理方式，针对controller方法执行时候，会查找到对应的APIImpl的实例，反射执行APIImpl中的方法。

```
public class ControllerInterceptor implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerInterceptor.class);

    private Object delegate;

    public ControllerInterceptor(Object delegate) {
        this.delegate = delegate;
    }

    /**
     * @param proxy  代理对象
     * @param method 代理方法
     * @param args   方法参数
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LOGGER.info("bytebuddy delegate proxy before");
        LOGGER.info("delegate ===>{}", delegate);
        LOGGER.info("method ===>{}", method.getName());
        LOGGER.info("args ===>{}", JSON.toJSONString(args));
        Method realMethod = ReflectionUtils.findMethod(delegate.getClass(), method.getName(), method.getParameterTypes());
        LOGGER.info("realMethod==>{}", realMethod);
        Object ret = ReflectionUtils.invokeMethod(realMethod, delegate, args);
        LOGGER.info("bytebuddy delegate proxy end");
        return ret;
    }
}
```

5、打印生成的controller的class类文件，便于观察是否生成正常

```java
    private void saveIn(DynamicType.Unloaded<?> dynamicType) {
        try {
            dynamicType.saveIn(new File(SAVE_FILE_PATH));
        } catch (IOException e) {
            LOGGER.error("saveIn error ", e);
        }
    }
```

6、最终将controller类注册到spring中

特别说明：旧版本的spring还需要需要手动将controller移除或者注入到handlermapping里，新版本的只要注入到spring容器后mvc框架会自己解析。（这里使用新版本的spring因此省略了注册到handlermapping的动作）

```
private void registerControllerBeanDefinition(BeanDefinitionRegistry registry, Class<?> beanClazz) {
    BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClazz);
    GenericBeanDefinition definition = (GenericBeanDefinition) beanDefinitionBuilder.getRawBeanDefinition();
    String simpleName = beanClazz.getSimpleName();
    // 将controller注册到
    registry.registerBeanDefinition(simpleName, definition);
    LOGGER.info("register controller {} bean definition: {}", simpleName, definition);
}
```

实测效果：
![image](https://raw.githubusercontent.com/lqnasa/dynamic-spring-controller/master/docs/images/image-20210424014829852.png)

运行效果：

![image](https://raw.githubusercontent.com/lqnasa/dynamic-spring-controller/master/docs/images/image-20210424015845156.png)



![image](https://raw.githubusercontent.com/lqnasa/dynamic-spring-controller/master/docs/images/image-20210424015111208.png)

## 五、总结

通过以上案例说明，为API的接口通过动态生成controller的方法，来实现对于增加API接口的可测性可行性。

后期还可以针对byte-buddy强大的库，做更多的案例实践：如 基于JavaAgent的实现全链路监控（APM实现）

源码地址：https://github.com/lqnasa/dynamic-spring-controller

参考项目
https://github.com/hiwepy/spring-bytebuddy


