package at.v3rtumnus.planman.entity;

import lombok.Data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
@Data
public class UserProfile {

    @Id
    private String username;
    private String password;
}
