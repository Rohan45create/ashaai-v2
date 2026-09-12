package com.ashaai.backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

public class AshaAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final UUID ashaId;
    private final UUID ashaHeadId;
    private final UUID authUserId;

    public AshaAuthenticationToken(Object principal, UUID ashaId, UUID ashaHeadId, UUID authUserId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.ashaId = ashaId;
        this.ashaHeadId = ashaHeadId;
        this.authUserId = authUserId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    public UUID getAshaId() {
        return ashaId;
    }

    public UUID getAshaHeadId() {
        return ashaHeadId;
    }
    
    public UUID getAuthUserId() {
        return authUserId;
    }
}
