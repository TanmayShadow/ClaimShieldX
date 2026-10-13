CREATE TABLE refresh_token(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(512) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_refresh_token_value ON refresh_token(token);
CREATE UNIQUE INDEX idx_refresh_token_user_id ON refresh_token(user_id);



-- @Entity
-- @Table(name = "refresh_token")
-- @Getter
-- @Setter
-- public class RefreshToken {
--     @Id
--     @GeneratedValue(strategy = GenerationType.UUID)
--     private UUID id;
--
--     @OneToOne
-- @JoinColumn(name = "user_id", referencedColumnName = "id")
--     private User user;
--
--     @Column(nullable = false, unique = true)
--     private String token;
--
--     @Column(nullable = false)
--     private Instant expiryDate;
-- }