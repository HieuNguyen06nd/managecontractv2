package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.service.OnlyOfficeEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeEditorService editorService;

    @GetMapping("/config/contracts/{id}")
    public Map<String, Object> getConfig(@PathVariable Long id) {
        return editorService.buildEditorConfigForContract(id);
    }
}
