```
frontend：npm install， npm run dev
backend：sbt (clean) run
```

****

在XXX-service/src/main/resources中修改url,user,password为对应数据库，并执行以下初始化（任意位置，命令行）

```
psql -U username -d dbname
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
  teacher_id TEXT NOT NULL
);

CREATE TABLE enrollments (
  student_id TEXT NOT NULL,
  course_id TEXT NOT NULL,
  PRIMARY KEY (student_id, course_id),
  FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
```

XXX-service目录下执行sbt run启动或sbt test测试

XXX-service/src/main/scala/Main.scala中实现了api

- user/register、login、update、info
- course/list?name=、create、select、drop、delete

