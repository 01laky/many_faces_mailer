package manyfaces.mailer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailerAuthInterceptorTest {

	@Mock
	private ServerCall<Object, Object> call;

	@Mock
	private ServerCallHandler<Object, Object> next;

	@Mock
	private ServerCall.Listener<Object> listener;

	@Test
	void healthCheckBypassesTokenWhenSecretConfigured() {
		stubMethod("grpc.health.v1.Health/Check");
		when(next.startCall(any(), any())).thenReturn(listener);

		var ic = new MailerAuthInterceptor("secret");
		ic.interceptCall(call, new Metadata(), next);

		verify(next).startCall(eq(call), any(Metadata.class));
		verify(call, never()).close(any(), any());
	}

	@Test
	void reflectionRpcBypassesToken() {
		stubMethod("grpc.reflection.v1.ServerReflection/ServerReflectionInfo");
		when(next.startCall(any(), any())).thenReturn(listener);

		var ic = new MailerAuthInterceptor("secret");
		ic.interceptCall(call, new Metadata(), next);

		verify(next).startCall(eq(call), any(Metadata.class));
		verify(call, never()).close(any(), any());
	}

	@Test
	void openWhenExpectedTokenUnset() {
		stubMethod("manyfaces.mailer.v1.MailerService/SendTemplatedEmail");
		when(next.startCall(any(), any())).thenReturn(listener);

		var ic = new MailerAuthInterceptor("");
		ic.interceptCall(call, new Metadata(), next);

		verify(next).startCall(eq(call), any(Metadata.class));
		verify(call, never()).close(any(), any());
	}

	@Test
	void sendEmailRejectsMissingToken() {
		stubMethod("manyfaces.mailer.v1.MailerService/SendTemplatedEmail");

		var ic = new MailerAuthInterceptor("secret");
		ic.interceptCall(call, new Metadata(), next);

		verifyUnauthenticatedClose();
		verify(next, never()).startCall(any(), any());
	}

	@Test
	void sendEmailAllowsMatchingToken() {
		stubMethod("manyfaces.mailer.v1.MailerService/SendTemplatedEmail");
		when(next.startCall(any(), any())).thenReturn(listener);

		Metadata headers = new Metadata();
		headers.put(MailerAuthInterceptor.MAILER_WORKER_TOKEN_KEY, "secret");

		var ic = new MailerAuthInterceptor("secret");
		ic.interceptCall(call, headers, next);

		verify(next).startCall(eq(call), eq(headers));
		verify(call, never()).close(any(), any());
	}

	@Test
	void sendEmailRejectsWrongToken() {
		stubMethod("manyfaces.mailer.v1.MailerService/SendTemplatedEmail");

		Metadata headers = new Metadata();
		headers.put(MailerAuthInterceptor.MAILER_WORKER_TOKEN_KEY, "wrong");

		var ic = new MailerAuthInterceptor("secret");
		ic.interceptCall(call, headers, next);

		verifyUnauthenticatedClose();
		verify(next, never()).startCall(any(), any());
	}

	@Test
	void constructorTrimsExpectedToken() {
		stubMethod("manyfaces.mailer.v1.MailerService/SendTemplatedEmail");
		when(next.startCall(any(), any())).thenReturn(listener);

		Metadata headers = new Metadata();
		headers.put(MailerAuthInterceptor.MAILER_WORKER_TOKEN_KEY, "secret");

		var ic = new MailerAuthInterceptor("  secret  ");
		ic.interceptCall(call, headers, next);

		verify(next).startCall(eq(call), eq(headers));
	}

	private void stubMethod(String fullMethodName) {
		MethodDescriptor<Object, Object> md = mock(MethodDescriptor.class);
		when(md.getFullMethodName()).thenReturn(fullMethodName);
		when(call.getMethodDescriptor()).thenReturn(md);
	}

	private void verifyUnauthenticatedClose() {
		verify(call)
				.close(
						argThat(
								(Status s) ->
										s.getCode() == Status.Code.UNAUTHENTICATED
												&& "missing or invalid x-mailer-worker-token"
														.equals(s.getDescription())),
						any());
	}
}
