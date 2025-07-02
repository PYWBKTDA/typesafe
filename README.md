```
frontend：npm install， npm run dev
backend：sbt (clean) run
```

****

在XXX-service/src/main/resources中修改url,user,password为对应数据库，并执行以下初始化

```
psql -U user -d dbname
（password）
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  uid TEXT PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password TEXT NOT NULL,
  info TEXT NOT NULL
);

CREATE TABLE courses (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  uid TEXT NOT NULL,
  teacher_name TEXT NOT NULL,
  time TEXT NOT NULL,
  location TEXT NOT NULL
);

CREATE TABLE enrollments (
  uid TEXT NOT NULL,
  course_id TEXT NOT NULL,
  PRIMARY KEY (uid, course_id),
  FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
```

XXX-service目录下执行sbt run启动或sbt test测试，其中course-service的测试需要数据库中执行

```
DELETE FROM users;

INSERT INTO users (uid, username, password, info)
VALUES (
  't-123',
  'teacher1',
  '$2a$10$PBdfjv8HAcNEaRbGU6X6eO0zjtaC8owYi62kp4hAlE14NKssKRBV6',
  '{"teacherId": "t-123", "name": "T", "department": "CS", "type": "teacher"}'
)
ON CONFLICT (uid) DO NOTHING;

INSERT INTO users (uid, username, password, info)
VALUES (
  's-456',
  'student1',
  '$2a$10$PBdfjv8HAcNEaRbGU6X6eO0zjtaC8owYi62kp4hAlE14NKssKRBV6',
  '{"studentId": "s-456", "name": "S", "department": "CS", "type": "student"}'
)
ON CONFLICT (uid) DO NOTHING;

INSERT INTO users (uid, username, password, info)
VALUES (
  't-another',
  'teacher2',
  '$2a$10$PBdfjv8HAcNEaRbGU6X6eO0zjtaC8owYi62kp4hAlE14NKssKRBV6',
  '{"teacherId": "t-another", "name": "T2", "department": "Math", "type": "teacher"}'
)
ON CONFLICT (uid) DO NOTHING;
```

XXX-service/src/main/scala/Main.scala中实现了api

- user/register、login、update、uid、info
- course/create、update、delete、select、drop、list、info、check、students

