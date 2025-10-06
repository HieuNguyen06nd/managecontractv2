package com.hieunguyen.ManageContract.service;

import java.util.Map;

public interface OnlyOfficeEditorService {
    Map<String, Object> buildEditorConfigForContract(Long contractId);
}