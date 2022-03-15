package com.lp.robot.gate.obj;

import java.math.BigDecimal;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-14 10:06<br/>
 * @since JDK 1.8
 */
public class MdResultObj {

    /**
     * 上一个MD值
     */
    private BigDecimal previous;
    /**
     * 当前MD值
     */
    private BigDecimal current;

    /**
     * 基数：5分钟线/15分钟线
     */
    private int base;

    /**
     * MD几，MD5/MD10
     */
    private int index;

    public String getKey() {
        return this.index + "_" + this.base;
    }

    public void setBase(int base) {
        this.base = base;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public BigDecimal getPrevious() {
        return previous;
    }

    public void setPrevious(BigDecimal previous) {
        this.previous = previous;
    }

    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }
}