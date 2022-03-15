package com.lp.robot.dextools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-10 10:36<br/>
 * @since JDK 1.8
 */
@Data
public class Config {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("`key`")
    private String key;

    private String val;

}