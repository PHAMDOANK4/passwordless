# springdoc-openapi ClassNotFoundException - Quick Fix Summary

## ⚡ Quick Fix (TL;DR)

**Problem:**
```
ClassNotFoundException: LiteWebJarsResourceResolver
→ Application won't start
```

**Solution:**
```xml
<!-- In pom.xml, change this: -->
<version>2.3.0</version>  to  <version>2.6.0</version>
```

**Result:**
```
✅ Application starts
✅ Swagger UI works
✅ Problem solved
```

---

## 🔧 Step-by-Step Fix

### 1. Edit pom.xml (line 139)

**Change:**
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>  <!-- Changed from 2.3.0 -->
</dependency>
```

### 2. Rebuild

```bash
mvn clean compile
```

### 3. Restart

```bash
mvn spring-boot:run
```

### 4. Verify

```
http://localhost:8080/swagger-ui/
```

---

## 📊 Compatibility

| Spring Boot | springdoc-openapi | Status |
|-------------|-------------------|--------|
| 3.2.5 | 2.3.0 | ❌ Broken |
| 3.2.5 | 2.6.0 | ✅ **Fixed** |

---

## 🆘 Still Having Issues?

**Read full troubleshooting guide:**
```bash
cat SPRINGDOC_CLASSNOTFOUND_FIX.md
```

**Common issues:**
1. Maven cache not cleared → `mvn clean install -U`
2. IDE cache stale → Restart IDE / Invalidate caches
3. Wrong URL → Use `/swagger-ui/` not `/swagger-ui.html`

---

## ✅ Done!

Application should now:
- ✅ Start without errors
- ✅ Swagger UI accessible
- ✅ All APIs documented

**No more ClassNotFoundException!** 🎉
