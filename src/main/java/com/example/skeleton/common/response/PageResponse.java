package com.example.skeleton.common.response;

import java.util.List;
import lombok.Builder;

/**
 * 목록 조회 공통 응답.
 * 최상위 배열은 필드를 추가할 수 없어 나중에 페이지네이션 메타를 넣으면 깨지는 변경이 되므로, 목록은 항상 이 타입으로 감쌈.
 * 성공/실패 메타를 담는 봉투가 아니라 컬렉션이라는 리소스 자체의 표현임.
 *
 * isFirst/hasPrevious/isLast/numberOfElements 같은 파생 가능 필드는 두지 않음.
 * 각각 {@code page == 1}, {@code page > 1}, {@code !hasNext}, {@code items.size()}로 클라이언트가 계산할 수 있는 중복임.
 *
 * @param items 현재 페이지의 데이터
 * @param page 현재 페이지 (1부터 시작)
 * @param size 페이지당 최대 데이터 개수
 * @param totalElements 전체 데이터 개수
 * @param totalPages 전체 페이지 수
 * @param hasNext 다음 페이지 존재 여부
 */
@Builder
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public PageResponse {
        items = List.copyOf(items);
    }

    /** 페이지네이션 없는 전체 목록. 저장소가 페이징을 지원하게 되면 {@link #of}로 갈아탐. */
    public static <T> PageResponse<T> all(List<T> items) {
        return PageResponse.<T>builder()
                .items(items)
                .page(1)
                .size(items.size())
                .totalElements(items.size())
                .totalPages(1)
                .hasNext(false)
                .build();
    }

    /** 페이징된 결과. totalPages와 hasNext는 여기서만 계산함. 호출부가 직접 셈하지 않음. */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return PageResponse.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page < totalPages)
                .build();
    }
}
