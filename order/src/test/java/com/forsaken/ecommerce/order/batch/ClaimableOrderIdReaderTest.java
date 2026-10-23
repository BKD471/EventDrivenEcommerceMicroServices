package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimableOrderIdReaderTest {

    private final IOrderRepository repository = mock(IOrderRepository.class);

    @Test
    void shouldPageByKeysetUntilExhausted() {
        when(repository.findClaimableIdsBySagaStatus(any(), any(), eq(0), any(Pageable.class)))
                .thenReturn(List.of(1, 3));
        when(repository.findClaimableIdsBySagaStatus(any(), any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(7));
        final ClaimableOrderIdReader reader =
                new ClaimableOrderIdReader(repository, OrderSagaStatus.RECOVERABLE, 2);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo(1);
        assertThat(reader.read()).isEqualTo(3);
        assertThat(reader.read()).isEqualTo(7);
        assertThat(reader.read()).isNull();
    }

    @Test
    void shouldResumeAfterLastReadIdOnRestart() {
        when(repository.findClaimableIdsBySagaStatus(any(), any(), eq(5), any(Pageable.class)))
                .thenReturn(List.of(9));
        final ClaimableOrderIdReader reader =
                new ClaimableOrderIdReader(repository, OrderSagaStatus.RECOVERABLE, 10);
        final ExecutionContext context = new ExecutionContext();
        context.putInt(ClaimableOrderIdReader.LAST_READ_ID, 5);
        reader.open(context);

        assertThat(reader.read()).isEqualTo(9);
        reader.update(context);

        assertThat(context.getInt(ClaimableOrderIdReader.LAST_READ_ID)).isEqualTo(9);
        verify(repository).findClaimableIdsBySagaStatus(any(), any(), eq(5), any(Pageable.class));
    }
}
