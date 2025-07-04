前端：

后端：XXX-survice中执行sbt run运行或sbt test测试

### 数据库

以下dbname、username、password需要改为自己的

##### aplication.conf

XXX-survice/src/main/resources/application.conf（jwt只在user-service的application.conf中添加）

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

### api

##### user-service

###### POST /user/register

**Body:** username, password, confirmPassword, info

| 情况             | 状态码 | 返回 JSON                                                    |
| ---------------- | ------ | ------------------------------------------------------------ |
| 成功注册         | 200    | {"status": "success", "message": "Registered"}               |
| 用户名重复       | 400    | {"status": "error", "message": "Username exists"}            |
| 密码不一致       | 400    | {"status": "error", "message": "Passwords do not match"}     |
| 用户名或密码为空 | 400    | {"status": "error", "message": "Username or password cannot be empty"} |

###### POST /user/login

**Body:** username, password

| 情况             | 状态码 | 返回 JSON                                                    |
| ---------------- | ------ | ------------------------------------------------------------ |
| 登录成功         | 200    | {"status": "success", "message": "Login successful", "data": {"token": ...}} |
| 用户名不存在     | 401    | {"status": "error", "message": "Invalid credentials"}        |
| 密码错误         | 401    | {"status": "error", "message": "Invalid credentials"}        |
| 用户名或密码为空 | 400    | {"status": "error", "message": "Username or password cannot be empty"} |

###### POST /user/update

**Header:** Authorization

**Body:** oldPassword?, newPassword?, confirmNewPassword?, info

| 情况               | 状态码 | 返回 JSON                                                    |
| ------------------ | ------ | ------------------------------------------------------------ |
| 只更新用户信息     | 200    | {"status": "success", "message": "Info updated"}             |
| 更新信息和密码     | 200    | {"status": "success", "message": "Info and password updated"} |
| 旧密码错误         | 401    | {"status": "error", "message": "Old password incorrect"}     |
| 新密码与确认不一致 | 400    | {"status": "error", "message": "New passwords do not match"} |
| 密码字段不完整     | 400    | {"status": "error", "message": "Incomplete password fields"} |
| 缺少 token         | 401    | {"status": "error", "message": "No token"}                   |
| token 无效         | 401    | {"status": "error", "message": "Invalid token"}              |

###### GET /user/uid

**Header:** Authorization

| 情况       | 状态码 | 返回 JSON                                       |
| ---------- | ------ | ----------------------------------------------- |
| 获取成功   | 200    | {"status": "success", "data": {"uid": ...}}     |
| 缺少 token | 401    | {"status": "error", "message": "No token"}      |
| token 无效 | 401    | {"status": "error", "message": "Invalid token"} |

###### GET /user/info

**Query:** uid

| 情况       | 状态码 | 返回 JSON                                                    |
| ---------- | ------ | ------------------------------------------------------------ |
| 获取成功   | 200    | {"status": "success", "data": {"uid": ..., "name": ..., "type": ...}} |
| uid 不存在 | 404    | {"status": "error", "message": "User not found"}             |

##### course-service

###### POST /course/create

**Header:** Authorization

**Body:** name, time, location

| 情况       | 状态码 | 返回 JSON                                                    |
| ---------- | ------ | ------------------------------------------------------------ |
| 创建成功   | 200    | {"status": "success", "message": "Course created"}           |
| 课程已存在 | 400    | {"status": "error", "message": "Course already exists"}      |
| 非教师身份 | 403    | {"status": "error", "message": "student not allowed to create course"} |
| 缺少 token | 401    | {"status": "error", "message": "No token"}                   |
| token 无效 | 401    | {"status": "error", "message": "Invalid token"}              |

###### POST /course/update

**Header:** Authorization

**Body:** courseId, name, time, location

| 情况         | 状态码 | 返回 JSON                                              |
| ------------ | ------ | ------------------------------------------------------ |
| 更新成功     | 200    | {"status": "success", "message": "Info updated"}       |
| 非教师身份   | 403    | {"status": "error", "message": "Only teacher allowed"} |
| 非课程创建者 | 403    | {"status": "error", "message": "Not owner"}            |
| 课程不存在   | 404    | {"status": "error", "message": "Course not found"}     |
| 缺少 token   | 401    | {"status": "error", "message": "No token"}             |
| token 无效   | 401    | {"status": "error", "message": "Invalid token"}        |

###### POST /course/delete

**Header:** Authorization

**Body:** courseId

| 情况         | 状态码 | 返回 JSON                                              |
| ------------ | ------ | ------------------------------------------------------ |
| 删除成功     | 200    | {"status": "success", "message": "Course deleted"}     |
| 非教师身份   | 403    | {"status": "error", "message": "Only teacher allowed"} |
| 非课程创建者 | 403    | {"status": "error", "message": "Not owner"}            |
| 课程不存在   | 404    | {"status": "error", "message": "Course not found"}     |
| 缺少 token   | 401    | {"status": "error", "message": "No token"}             |
| token 无效   | 401    | {"status": "error", "message": "Invalid token"}        |

###### POST /course/select

**Header:** Authorization

**Body:** courseId

| 情况         | 状态码 | 返回 JSON                                                 |
| ------------ | ------ | --------------------------------------------------------- |
| 选课成功     | 200    | {"status": "success", "message": "Enrolled"}              |
| 非学生身份   | 403    | {"status": "error", "message": "Only student can enroll"} |
| 已选过该课程 | 400    | {"status": "error", "message": "Already enrolled"}        |
| 课程不存在   | 404    | {"status": "error", "message": "Course not found"}        |
| 缺少 token   | 401    | {"status": "error", "message": "No token"}                |
| token 无效   | 401    | {"status": "error", "message": "Invalid token"}           |

###### POST /course/drop

**Header:** Authorization

**Body:** courseId

| 情况       | 状态码 | 返回 JSON                                               |
| ---------- | ------ | ------------------------------------------------------- |
| 退课成功   | 200    | {"status": "success", "message": "Dropped"}             |
| 非学生身份 | 403    | {"status": "error", "message": "Only student can drop"} |
| 未选该课程 | 404    | {"status": "error", "message": "Not enrolled"}          |
| 课程不存在 | 404    | {"status": "error", "message": "Course not found"}      |
| 缺少 token | 401    | {"status": "error", "message": "No token"}              |
| token 无效 | 401    | {"status": "error", "message": "Invalid token"}         |

###### GET /course/check

**Query:** courseId, uid

| 情况             | 状态码 | 返回 JSON                                                    |
| ---------------- | ------ | ------------------------------------------------------------ |
| 学生已选该课程   | 200    | {"status": "success", "message": "Checked", "data": {"status": "Selected"}} |
| 教师创建了该课程 | 200    | {"status": "success", "message": "Checked", "data": {"status": "Created"}} |
| 学生未选该课程   | 200    | {"status": "success", "message": "Checked", "data": {"status": "Not selected"}} |
| 教师未创建该课程 | 200    | {"status": "success", "message": "Checked", "data": {"status": "Not created"}} |
| uid 不存在       | 404    | {"status": "error", "message": "User not found"}             |
| courseId 不存在  | 404    | {"status": "error", "message": "Course not found"}           |

###### GET /course/list

无参数

| 情况     | 状态码 | 返回 JSON                                                    |
| -------- | ------ | ------------------------------------------------------------ |
| 查询成功 | 200    | {"status": "success", "message": "Course list", "data": [Course, ...]} |

###### GET /course/info

**Query:** id

| 情况       | 状态码 | 返回 JSON                                                    |
| ---------- | ------ | ------------------------------------------------------------ |
| 查询成功   | 200    | {"status": "success", "message": "Course info", "data": {Course}} |
| 课程不存在 | 404    | {"status": "error", "message": "Course not found"}           |

###### GET /course/students

**Header:** Authorization

**Query:** id

| 情况         | 状态码 | 返回 JSON                                                    |
| ------------ | ------ | ------------------------------------------------------------ |
| 查询成功     | 200    | {"status": "success", "message": "Student list", "data": [uid, ...]} |
| 非教师身份   | 403    | {"status": "error", "message": "Only teacher allowed"}       |
| 非课程拥有者 | 403    | {"status": "error", "message": "Not owner"}                  |
| 课程不存在   | 404    | {"status": "error", "message": "Course not found"}           |
| 缺少 token   | 401    | {"status": "error", "message": "No token"}                   |
| token 无效   | 401    | {"status": "error", "message": "Invalid token"}              |

