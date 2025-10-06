package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;

// InternalFileController.java
@RestController
public class InternalFileController {
    @GetMapping(
            value = "/internal/files/{id}/contract.docx",
            produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<Resource> serveDocx(@PathVariable Long id) {
        Path p = docxPathOf(id);
        if (!Files.exists(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"contract.docx\"")
                .body(new FileSystemResource(p));
    }

    private Path docxPathOf(Long id) {
        return Paths.get(System.getProperty("user.dir"),
                "uploads","contracts",String.valueOf(id),"contract.docx");
    }
}
