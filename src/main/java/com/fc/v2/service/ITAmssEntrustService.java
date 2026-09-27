package com.fc.v2.service;

import com.fc.v2.model.auto.TAmssEntrust;

import java.util.List;

/**
 * 跨站协办受托登记 Service接口
 * <p>
 * 受托哪一家，受托站才瞧得见那一家；卸了托即刻瞧不见。
 *
 * @author fuce
 * @date 2026-09-27
 */
public interface ITAmssEntrustService {

    /** 登记受托：同一底档对同一站在托只保一条，返回登记条目 */
    TAmssEntrust grant(String siteNo, String stationName, String operator);

    /** 卸托：登记条目撤下（留痕不删），返回卸托后的条目 */
    TAmssEntrust relieve(Long id, String operator);

    /** 某站当前在托的受托条目 */
    List<TAmssEntrust> listByStation(String stationName);
}
