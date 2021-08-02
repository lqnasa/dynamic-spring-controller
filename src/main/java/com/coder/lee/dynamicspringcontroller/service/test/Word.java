package com.coder.lee.dynamicspringcontroller.service.test;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;

/**
 * Description: Function Description
 * Copyright: Copyright (c)
 * Company: Ruijie Co., Ltd.
 * Create Time: 2021/4/23 12:44
 *
 * @author coderLee23
 */
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Data
public class Word {

    @NotBlank
    @JSONField(name = "word_xx")
    private String word;

}
