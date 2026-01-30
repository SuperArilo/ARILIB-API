package com.tty.api.dto;

import java.util.List;

/**
 * @param records     当前页数据
 * @param total       总记录数
 * @param totalPages  总页数
 * @param currentPage 当前页码
 */
public record PageResult<T>(List<T> records, long total, long totalPages, long currentPage) {

    public static <T> PageResult<T> build(List<T> records, long total, long totalPages, long currentPage) {
        return new PageResult<>(records, total, totalPages, currentPage);
    }

}