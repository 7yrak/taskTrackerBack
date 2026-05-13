package com.tasktracker.service;

import com.tasktracker.dto.TeamMemberDTO;
import com.tasktracker.dto.TeamMemberRequest;
import com.tasktracker.model.TeamMember;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class TeamMemberService {

    private final TeamMemberRepository memberRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<TeamMemberDTO> findAll() {
        return memberRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TeamMemberDTO findById(Long id) {
        return memberRepository.findById(id).map(this::toDTO)
            .orElseThrow(() -> new NoSuchElementException("Miembro no encontrado: " + id));
    }

    public TeamMemberDTO create(TeamMemberRequest req) {
        if (memberRepository.existsByEmail(req.email()))
            throw new IllegalArgumentException("Ya existe un miembro con email: " + req.email());
        TeamMember member = TeamMember.builder()
            .name(req.name())
            .email(req.email())
            .role(req.role())
            .build();
        return toDTO(memberRepository.save(member));
    }

    public TeamMemberDTO update(Long id, TeamMemberRequest req) {
        TeamMember member = memberRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Miembro no encontrado: " + id));
        member.setName(req.name());
        member.setEmail(req.email());
        member.setRole(req.role());
        return toDTO(memberRepository.save(member));
    }

    public void delete(Long id) {
        if (!memberRepository.existsById(id))
            throw new NoSuchElementException("Miembro no encontrado: " + id);
        memberRepository.deleteById(id);
    }

    private TeamMemberDTO toDTO(TeamMember m) {
        List<String> projectNames = m.getProjects().stream()
            .map(p -> p.getName())
            .toList();
        long taskCount = taskRepository.findByAssigneesId(m.getId()).size();
        return new TeamMemberDTO(
            m.getId(), m.getName(), m.getEmail(), m.getRole(),
            m.getCreatedAt(), projectNames, taskCount
        );
    }
}
