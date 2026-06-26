package com.wahyuzero.replyforge.ui.main

import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.db.HistoryDao
import com.wahyuzero.replyforge.data.model.ReplyHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = mock()
        historyDao = mock()
        whenever(db.historyDao()).thenReturn(historyDao)
        whenever(historyDao.getAllHistory()).thenReturn(flowOf(emptyList()))
        viewModel = HistoryViewModel(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty history`() = runTest {
        assertThat(viewModel.history.value, empty())
    }

    @Test
    fun `history loaded from DAO`() = runTest {
        val testHistory = listOf(
            ReplyHistory(sender = "Alice", message = "Hi", response = "Hello!"),
            ReplyHistory(sender = "Bob", message = "Yo", response = "Hey!")
        )
        whenever(historyDao.getAllHistory()).thenReturn(flowOf(testHistory))
        viewModel = HistoryViewModel(db)

        assertThat(viewModel.history.value, equalTo(testHistory))
    }

    @Test
    fun `isLoading is false after initial load`() = runTest {
        assertThat(viewModel.isLoading.value, `is`(false))
    }
}
