package com.coder.lee.dynamicspringcontroller.service.test.impl;


import com.coder.lee.dynamicspringcontroller.service.test.SayAPI;
import com.coder.lee.dynamicspringcontroller.service.test.Word;

import javax.validation.constraints.NotBlank;

/**
 * Description: Function Description
 * Copyright: Copyright (c)
 * Company: Ruijie Co., Ltd.
 * Create Time: 2021/4/15 9:19
 *
 * @author coderLee23
 */
public class SayAPIImpl implements SayAPI {

    @Override
    public String say(String word) {
        return "说:" + word;
    }

    @Override
    public String say(Word word) {
        return "说:" + word.toString();
    }

    @Override
    public String say() {
        return "说什么好呢！";
    }

}
