package com.coder.lee.dynamicspringcontroller.interceptor;


import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Description: ControllerInterceptor
 * Copyright: Copyright (c)
 * Company: Ruijie Co., Ltd.
 * Create Time: 2021/4/20 1:33
 *
 * @author coderLee23
 */
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
