plugins {
	java
	application
	id("com.google.protobuf") version "0.9.4"
}

group = "manyfaces"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

application {
	mainClass.set("manyfaces.mailer.MailerWorkerMain")
}

repositories {
	mavenCentral()
}

val grpcVersion = "1.76.0"
// protobuf-java Maven artifacts use 3.25.x (NuGet Google.Protobuf 3.34.x has no matching java:3.34.1 on Central).
val protobufVersion = "3.25.8"
val junitVersion = "5.11.4"

// Canonical .proto files live in nested many_faces_proto submodule (Strategy B) or MANY_FACES_PROTO_DIR for overrides.
val contractProtoRoot =
	System.getenv("MANY_FACES_PROTO_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { file(it) }
		?: file("many_faces_proto/proto")
if (!contractProtoRoot.exists()) {
	throw GradleException(
		"Proto directory missing: ${contractProtoRoot.absolutePath}. " +
			"Run: git submodule update --init --recursive (nested many_faces_proto), or set MANY_FACES_PROTO_DIR.",
	)
}

dependencies {
	implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
	implementation("io.grpc:grpc-protobuf:$grpcVersion")
	implementation("io.grpc:grpc-stub:$grpcVersion")
	implementation("io.grpc:grpc-services:$grpcVersion")
	implementation("com.google.protobuf:protobuf-java:$protobufVersion")
	implementation("javax.annotation:javax.annotation-api:1.3.2")
	implementation("org.eclipse.angus:angus-mail:2.0.3")
	implementation("io.pebbletemplates:pebble:3.2.2")
	implementation("org.slf4j:slf4j-api:2.0.16")
	implementation("net.logstash.logback:logstash-logback-encoder:7.4")
	runtimeOnly("ch.qos.logback:logback-classic:1.5.12")

	testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
	testImplementation("org.assertj:assertj-core:3.26.3")
	testImplementation("io.grpc:grpc-testing:$grpcVersion")
	testImplementation("org.mockito:mockito-core:5.14.2")
	testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:$protobufVersion"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				create("grpc")
			}
		}
	}
}

sourceSets {
	main {
		proto {
			srcDir(contractProtoRoot)
		}
	}
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-parameters")
}

tasks.withType<Jar>().configureEach {
	manifest {
		attributes(
			mapOf(
				"Implementation-Vendor" to "Ladislav Kostolny",
				"Contact" to "01laky@gmail.com",
			),
		)
	}
}
