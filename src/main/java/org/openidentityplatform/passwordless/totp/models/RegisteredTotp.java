package org.openidentityplatform.passwordless.totp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import org.openidentityplatform.passwordless.identity.models.AppUser;

@Entity(name = "registered_totps")
@Data
public class RegisteredTotp {
    @Id
    private String username;

    @Column
    private String secret; //TODO add encryption and decryption

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private AppUser appUser;

}
