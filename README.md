# Cloud Storage REST API (Backend)

A secure, multi-tenant cloud storage backend built with Spring Boot 3, Spring Security (JWT), Hibernate/JPA, and MySQL.

---

## Related Repository
- **Frontend Client:** [React + Vite Cloud Storage Frontend](https://github.com/sagarchoudhary6264/cloud-storage-frontend)

---

## Core Features

- **Stateless Authentication:** Custom JWT authentication filter (`JwtAuthenticationFilter`) with BCrypt password hashing.
- **Quota Management:** Strict per-user 20 MB storage limit validation before disk writes.
- **Multipart Upload Pipeline:** Secure UUID filename hashing to prevent path traversal and naming collisions.
- **Public Share Links:** Dynamic generation and immediate revocation of unauthenticated public download tokens.
- **Permanent File Deletion:** Synchronized cleanup between local filesystem disk storage and MySQL metadata records.
- **CORS & Preflight Handling:** Tailored `CorsConfigurationSource` allowing cross-origin requests from Vite dev server with exposed `Content-Disposition` download headers.

---

## Tech Stack

- **Language:** Java 17+
- **Framework:** Spring Boot 3.x (Spring Web, Spring Security, Spring Data JPA)
- **Database:** MySQL 8.x
- **Build Tool:** Maven
- **Security:** JJWT (JSON Web Token), BCrypt

---

## Local Setup & Configuration

### 1. Database Setup
Create your MySQL database:
```sql
CREATE DATABASE cloud_storage;