package com.wahyuzero.replyforge.ui.main

import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.db.HistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = mock()
        historyDao = mock()
        whenever(db.historyDao()).thenReturn(historyDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stats loaded correctly from DAO`() = runTest {
        whenever(historyDao.getTotalReplies()).thenReturn(100)
        whenever(historyDao.getRepliesToday(any(), any())).thenReturn(10)
        whenever(historyDao.getRepliesToContacts()).thenReturn(60)
        whenever(historyDao.getRepliesToGroups()).thenReturn(40)
        viewModel = StatsViewModel(db)

        val state = viewModel.state.value
        assertThat(state.totalReplies, equalTo(100))
        assertThat(state.repliesToday, equalTo(10))
        assertThat(state.repliesContacts, equalTo(60))
        assertThat(state.repliesGroups, equalTo(40))
        assertThat(state.isLoading, `is`(false))
    }

    @Test
    fun `refresh triggers new DAO calls`() = runTest {
        whenever(historyDao.getTotalReplies()).thenReturn(50)
        whenever(historyDao.getRepliesToday(any(), any())).thenReturn(5)
        whenever(historyDao.getRepliesToContacts()).thenReturn(30)
        whenever(historyDao.getRepliesToGroups()).thenReturn(20)
        viewModel = StatsViewModel(db)
        viewModel.refresh()

        verify(historyDao, atLeast(2)).getTotalReplies()
    }

    @Test
    fun `initial state has zero values before load`() = runTest {
        whenever(historyDao.getTotalReplies()).thenReturn(0)
        whenever(historyDao.getRepliesToday(any(), any())).thenReturn(0)
        whenever(historyDao.getRepliesToContacts()).thenReturn(0)
        whenever(historyDao.getRepliesToGroups()).thenReturn(0)
        viewModel = StatsViewModel(db)

        val state = viewModel.state.value
        assertThat(state.totalReplies, equalTo(0))
        assertThat(state.repliesToday, equalTo(0))
    }
}
