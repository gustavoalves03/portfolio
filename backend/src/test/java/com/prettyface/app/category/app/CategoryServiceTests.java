package com.prettyface.app.category.app;

import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CareRepository careRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private Category targetCategory;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Visage");
        category.setDescription("Soins du visage");

        targetCategory = new Category();
        targetCategory.setId(2L);
        targetCategory.setName("Corps");
        targetCategory.setDescription("Soins du corps");
    }

    @Test
    void listAll_returnsAllCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(category, targetCategory));
        List<CategoryResponse> result = categoryService.listAll();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Visage");
    }

    @Test
    void deleteWithReassignment_whenNoCares_deletesDirectly() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(0L);
        DeleteCategoryResponse response = categoryService.deleteWithReassignment(1L, null);
        verify(categoryRepository).deleteById(1L);
        assertThat(response.reassignedCaresCount()).isZero();
    }

    @Test
    void deleteWithReassignment_whenCaresExistAndNoTarget_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
    }

    @Test
    void deleteWithReassignment_whenCaresExistAndTargetProvided_reassigns() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(targetCategory));
        when(careRepository.reassignCategory(eq(1L), any())).thenReturn(3);
        DeleteCategoryResponse response = categoryService.deleteWithReassignment(1L, 2L);
        verify(careRepository).reassignCategory(1L, targetCategory);
        verify(categoryRepository).deleteById(1L);
        assertThat(response.reassignedCaresCount()).isEqualTo(3);
    }

    @Test
    void deleteWithReassignment_whenTargetNotFound_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteWithReassignment_whenTargetSameAsSource_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    @Test
    void deleteWithReassignment_whenSourceNotFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.deleteWithReassignment(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listAll_emptyList_returnsEmpty() {
        when(categoryRepository.findAll()).thenReturn(List.of());
        List<CategoryResponse> result = categoryService.listAll();
        assertThat(result).isEmpty();
    }
}
