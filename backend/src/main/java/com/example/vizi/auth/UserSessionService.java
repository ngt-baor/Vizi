package com.example.vizi.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    UserSessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    void revokeAll(String principalName) {
        sessions.findByPrincipalName(principalName)
                .keySet()
                .forEach(sessions::deleteById);
    }
}