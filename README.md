前端：npm run dev

后端：XXX-survice中执行sbt run运行或sbt test测试

##### aplication.conf

XXX-survice/src/main/resources/application.conf

- dbname、username、password需要改为自己的
- jwt只在user-service的application.conf中添加

```
jwt {
  secret = "your-secret-key"
}
```

```
db {
  driver = 'org.postgresql.Driver'
  url = 'jdbc:postgresql://localhost:5432/dbname'
  user = 'username'
  password = 'password'
  connectionPool = 'HikariCP'
  keepAliveConnection = true
}
```

##### 初始化

```
psql -U username -d dbname
（password）
```

```
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

##### api

user-service：register、login、update、uid、info

course-service：create、update、delete、select、drop、check、list、info、students

