package org.openidentityplatform.passwordless.webauthn.repositories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.openidentityplatform.passwordless.identity.models.AppUser;

import java.io.Serializable;

@Entity
@Table(name = "webauthn_authenticators")
@Getter
@Setter
@IdClass(WebAuthnAuthenticatorEntity.class)
public class WebAuthnAuthenticatorEntity implements Serializable {

    @Id
    @Column(name = "username")
    private String username;

    @Id
    @Column(name = "authenticator")
    private String authenticator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private AppUser appUser;

}
