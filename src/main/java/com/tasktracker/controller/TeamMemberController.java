package com.tasktracker.controller;

import com.tasktracker.dto.TeamMemberDTO;
import com.tasktracker.dto.TeamMemberRequest;
import com.tasktracker.service.TeamMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService memberService;

    @GetMapping
    public List<TeamMemberDTO> getAll() {
        return memberService.findAll();
    }

    @GetMapping("/{id}")
    public TeamMemberDTO getById(@PathVariable Long id) {
        return memberService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberDTO create(@Valid @RequestBody TeamMemberRequest req) {
        return memberService.create(req);
    }

    @PutMapping("/{id}")
    public TeamMemberDTO update(@PathVariable Long id, @Valid @RequestBody TeamMemberRequest req) {
        return memberService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        memberService.delete(id);
    }
}
