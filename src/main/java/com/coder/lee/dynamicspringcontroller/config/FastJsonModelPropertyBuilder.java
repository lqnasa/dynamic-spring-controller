package com.coder.lee.dynamicspringcontroller.config;

import com.alibaba.fastjson.annotation.JSONField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import springfox.bean.validators.plugins.Validators;
import springfox.documentation.builders.PropertySpecificationBuilder;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.ModelPropertyBuilderPlugin;
import springfox.documentation.spi.schema.contexts.ModelPropertyContext;

import java.lang.reflect.Field;
import java.util.Optional;

import static springfox.bean.validators.plugins.Validators.extractAnnotation;

/**
 * Description: 处理@JSONField问题
 * Copyright: Copyright (c)
 * Company: Ruijie Co., Ltd.
 * Create Time: 2021/8/1 22:17
 *
 * @author coderLee23
 */
@Component
@Order(Validators.BEAN_VALIDATOR_PLUGIN_ORDER + 100)
public class FastJsonModelPropertyBuilder implements ModelPropertyBuilderPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastJsonModelPropertyBuilder.class);

    @Override
    public void apply(ModelPropertyContext context) {
        Optional<JSONField> jsonField = extractAnnotation(context, JSONField.class);
        String jsonFieldFromAnnotation = jsonField.map(JSONField::name).orElse(null);
        if (StringUtils.hasText(jsonFieldFromAnnotation)) {
            context.getBuilder().name(jsonFieldFromAnnotation).description(jsonFieldFromAnnotation);
            //PropertySpecificationBuilder name 设置为 final String 因此需要通过反射赋值
            PropertySpecificationBuilder specificationBuilder = context.getSpecificationBuilder().description(jsonFieldFromAnnotation);
            try {
                Field field = specificationBuilder.getClass().getDeclaredField("name");
                field.setAccessible(true);
                field.set(specificationBuilder, jsonFieldFromAnnotation);
            } catch (Exception e) {
                LOGGER.error("反射处理异常！", e);
            }
        }
    }

    @Override
    public boolean supports(DocumentationType delimiter) {
        return true;
    }

}
