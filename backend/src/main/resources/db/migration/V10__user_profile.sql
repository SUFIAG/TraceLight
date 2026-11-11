-- Profile picture storage.
--
-- Images live in the database rather than on disk so a deployment needs no
-- shared volume and no object store to work, and so an avatar cannot outlive
-- the account that owns it. They are small by construction: the backend
-- re-encodes every upload to a bounded PNG before it ever reaches this table.

CREATE TABLE user_avatars (
    user_id      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(64) NOT NULL,
    image_bytes  BYTEA NOT NULL,
    size_bytes   INT NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Lets the user list say who has a picture without reading every image.
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN job_title VARCHAR(120);
ALTER TABLE users ADD COLUMN timezone VARCHAR(64);
