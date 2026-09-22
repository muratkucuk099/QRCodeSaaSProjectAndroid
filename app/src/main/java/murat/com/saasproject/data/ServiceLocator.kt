package murat.com.saasproject.data

import murat.com.saasproject.data.repository.AccountDeletionRepository
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.data.repository.NotificationRepository
import murat.com.saasproject.data.repository.PointsRepository
import murat.com.saasproject.data.repository.RewardRepository
import murat.com.saasproject.data.repository.StatsRepository
import murat.com.saasproject.data.repository.UserRepository

/**
 * Repository örneklerinin tek erişim noktası.
 *
 * Projede DI kütüphanesi (Hilt/Koin) kullanmak yerine hafif bir service locator tercih edildi:
 * repository'ler durumsuz (stateless) olduğu için tek örnek yeterli ve ViewModel'lar
 * constructor injection ile test edilebilir kalıyor.
 */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val businessRepository: BusinessRepository by lazy { BusinessRepository() }
    val userRepository: UserRepository by lazy { UserRepository() }
    val rewardRepository: RewardRepository by lazy { RewardRepository() }
    val pointsRepository: PointsRepository by lazy { PointsRepository() }
    val notificationRepository: NotificationRepository by lazy { NotificationRepository() }
    val statsRepository: StatsRepository by lazy { StatsRepository() }
    val accountDeletionRepository: AccountDeletionRepository by lazy { AccountDeletionRepository() }
}
