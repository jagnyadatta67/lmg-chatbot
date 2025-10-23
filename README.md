# 🤖 Landmark Online Chatbot

An intelligent AI-powered chatbot built with Spring Boot, OpenAI GPT-4, and Qdrant vector store for semantic search and context-aware responses.

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Cache Management](#cache-management)
- [Project Structure](#project-structure)
- [Usage Examples](#usage-examples)
- [Troubleshooting](#troubleshooting)

## ✨ Features

- 🧠 **AI-Powered Responses**: Leverages OpenAI GPT-4o-mini for intelligent conversations
- 🔍 **Semantic Search**: Uses Qdrant vector store for context-aware responses
- 📄 **PDF Processing**: Extract and process information from PDF documents
- 💾 **Intelligent Caching**: Caffeine-based caching for improved performance
- 🎯 **Intent Classification**: Smart routing based on user query intent
- 📊 **Cache Analytics**: Real-time cache statistics and management
- 🔐 **Secure**: Handles sensitive information with care
- 📚 **API Documentation**: Interactive Swagger UI for easy testing

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.5.6 | Application framework |
| Java | 21 | Programming language |
| Spring AI | 1.0.3 | AI integration |
| OpenAI GPT | 4o-mini | Language model |
| Qdrant | 1.9.0 | Vector database |
| MySQL | 8.x | Relational database |
| Caffeine Cache | Latest | In-memory caching |
| Maven | 3.x | Build tool |
| Lombok | Latest | Code generation |
| SpringDoc OpenAPI | 2.6.0 | API documentation |

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- ☕ **Java 21** or higher
- 🔧 **Maven 3.6+**
- 🐬 **MySQL 8.0+**
- 🔍 **Qdrant** (local or cloud instance)
- 🔑 **OpenAI API Key**

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/jagnyadatta67/lmg-chatbot.git
cd loipl
```

### 2. Set Up MySQL Database

```sql
CREATE DATABASE education_db;
```

### 3. Set Up Qdrant Vector Store

#### Option A: Local Installation (Docker)

```bash
docker run -p 6334:6334 -p 6333:6333 \
  -v $(pwd)/qdrant_storage:/qdrant/storage:z \
  qdrant/qdrant
```



### 4. Install Dependencies

```bash
mvn clean install
```

## ⚙️ Configuration

### Application Properties

Create or update `src/main/resources/application.properties`:

```properties
# Application Name
spring.application.name=Landmark Online Chatbot

# Server Configuration
server.port=8080

# ========================
# OpenAI Configuration
# ========================
spring.ai.openai.api-key=your-openai-api-key-here
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.chat.options.temperature=0.7
spring.ai.openai.embedding.model=text-embedding-3-small

# ========================
# Qdrant Vector Store Configuration
# ========================
spring.ai.vectorstore.qdrant.host=localhost
spring.ai.vectorstore.qdrant.port=6334
spring.ai.vectorstore.qdrant.use-tls=false
spring.ai.vectorstore.qdrant.api-key=

# For Qdrant Cloud, use:
# spring.ai.vectorstore.qdrant.host=your-cluster.cloud.qdrant.io
# spring.ai.vectorstore.qdrant.port=6333
# spring.ai.vectorstore.qdrant.use-tls=true
# spring.ai.vectorstore.qdrant.api-key=your-api-key

# ========================
# MySQL Database Configuration
# ========================
spring.datasource.url=jdbc:mysql://localhost:3306/education_db?useConfigs=maxPerformance&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=pass123
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA Configuration
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# ========================
# File Upload Configuration
# ========================
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# ========================
# Cache Configuration
# ========================
spring.cache.type=caffeine
spring.cache.cache-names=chatbotResponses,intentClassifications,userContext
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=1h,recordStats

# Custom Cache Settings
chatbot.cache.enabled=true
chatbot.cache.ttl-hours=1
chatbot.cache.max-size=1000
chatbot.cache.exclude-patterns[0]=.*credit card.*
chatbot.cache.exclude-patterns[1]=.*password.*
chatbot.cache.exclude-patterns[2]=.*pin.*
chatbot.cache.exclude-patterns[3]=.*now.*
chatbot.cache.exclude-patterns[4]=.*current.*

# ========================
# Swagger / OpenAPI Configuration
# ========================
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.doc-expansion=none
springdoc.swagger-ui.display-request-duration=true
springdoc.swagger-ui.tags-sorter=alpha
springdoc.swagger-ui.operations-sorter=alpha

# ========================
# Logging Configuration
# ========================
logging.level.root=INFO
logging.level.com.lmg.online.chatbot=DEBUG
logging.level.org.springframework.cache=DEBUG
```

### Environment Variables (Alternative)

For production, use environment variables:

```bash
export OPENAI_API_KEY=your-api-key
export QDRANT_HOST=your-qdrant-host
export QDRANT_API_KEY=your-qdrant-key
export MYSQL_PASSWORD=your-db-password
```

## 🏃 Running the Application

### Development Mode

```bash
mvn spring-boot:run
```

### Production Mode

```bash
# Build the application
mvn clean package

# Run the JAR file
java -jar target/loipl-0.0.1-SNAPSHOT.jar
```

### Docker (Optional)

```bash
# Build Docker image
docker build -t landmark-chatbot .

# Run container
docker run -p 8080:8080 landmark-chatbot
```

## 📚 API Documentation

Once the application is running, access the interactive API documentation:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Key Endpoints

#### Chat Endpoints

```http
POST /api/chat
Content-Type: application/json

{
  "message": "What is Spring Boot?",
  "userId": "user123"
}
```

#### Cache Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/cache/stats` | Get cache statistics |
| GET | `/api/cache/info` | Get cache information |
| DELETE | `/api/cache/clear` | Clear all caches |
| DELETE | `/api/cache/clear/{cacheName}` | Clear specific cache |
| GET | `/api/cache/{cacheName}/{key}` | Get cache entry |
| DELETE | `/api/cache/{cacheName}/{key}` | Evict cache entry |
| POST | `/api/cache/warmup` | Warm up cache |

## 🗄️ Cache Management

### View Cache Statistics

```bash
curl http://localhost:8080/api/cache/stats
```

Response:
```json
{
  "chatbotResponses": {
    "hitCount": 150,
    "missCount": 50,
    "hitRate": 0.75,
    "evictionCount": 10
  },
  "intentClassifications": {
    "hitCount": 200,
    "missCount": 30,
    "hitRate": 0.87
  }
}
```

### Clear Cache

```bash
# Clear all caches
curl -X DELETE http://localhost:8080/api/cache/clear

# Clear specific cache
curl -X DELETE http://localhost:8080/api/cache/clear/chatbotResponses
```

### Cache Warmup

```bash
curl -X POST http://localhost:8080/api/cache/warmup \
  -H "Content-Type: application/json" \
  -d '["What is Java?", "Explain Spring Boot", "What is REST API?"]'
```


```

## 💡 Usage Examples

### Basic Chat Query

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is machine learning?",
    "userId": "user123"
  }'
```

### Location-Based Query

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is the weather?",
    "userId": "user123",
    "latitude": 12.9716,
    "longitude": 77.5946
  }'
```

### Context-Aware Query

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Tell me more about it",
    "userId": "user123",
    "previousResponse": "Spring Boot is a framework..."
  }'
```

## 🔧 Troubleshooting

### Common Issues

#### 1. Cache Not Working

**Problem**: Responses are not being cached

**Solution**:
- Verify `@EnableCaching` is present in your configuration
- Check cache statistics: `GET /api/cache/stats`
- Ensure queries are cacheable (not time-sensitive or sensitive data)

#### 2. OpenAI API Connection Error

**Problem**: `401 Unauthorized` or connection timeout

**Solution**:
```bash
# Verify API key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer YOUR_API_KEY"
```

#### 3. Qdrant Connection Failed

**Problem**: Cannot connect to Qdrant

**Solution**:
```bash
# Check Qdrant is running
curl http://localhost:6333/collections

# For Docker
docker ps | grep qdrant
```

#### 4. MySQL Connection Error

**Problem**: `Access denied for user`

**Solution**:
```sql
-- Grant privileges
GRANT ALL PRIVILEGES ON education_db.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### Enable Debug Logging

Add to `application.properties`:

```properties
logging.level.com.lmg.online.chatbot=DEBUG
logging.level.org.springframework.ai=DEBUG
logging.level.org.springframework.cache=DEBUG
```

## 📊 Performance Metrics

With caching enabled, you can expect:

- ✅ **Cache Hit**: ~5-10ms response time
- ❌ **Cache Miss**: ~500-2000ms (OpenAI API call)
- 📈 **Cache Hit Rate**: 70-80% for common queries
- 💾 **Memory Usage**: ~50-100MB for 1000 cached entries

## 🔐 Security Best Practices

1. **Never commit API keys** to version control
2. Use environment variables for sensitive data
3. Implement rate limiting for production
4. Enable HTTPS in production
5. Sanitize user inputs
6. Implement authentication/authorization

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Authors

- **Your Name** - Initial work

## 🙏 Acknowledgments

- Spring AI Team
- OpenAI
- Qdrant Team
- Spring Boot Community

## 📞 Support

For support and questions:
- 📧 Email: support@landmark-chatbot.com
- 💬 Slack: [Join our workspace]
- 🐛 Issues: [GitHub Issues](https://github.com/your-org/landmark-online-chatbot/issues)

---

Made with ❤️ by the Landmark Team