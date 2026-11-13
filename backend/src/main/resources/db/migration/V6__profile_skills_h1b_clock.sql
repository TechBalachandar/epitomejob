CREATE TABLE profile.profile_skills (
  profile_id UUID NOT NULL REFERENCES profile.profiles(id) ON DELETE CASCADE,
  skill VARCHAR(60) NOT NULL,
  PRIMARY KEY (profile_id, skill)
);
CREATE INDEX idx_profile_skills_profile ON profile.profile_skills (profile_id);
ALTER TABLE profile.profiles ADD COLUMN h1b_clock_start_date DATE;
