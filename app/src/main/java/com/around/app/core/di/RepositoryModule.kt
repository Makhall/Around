package com.around.app.core.di

import com.around.app.data.auth.AuthRepository
import com.around.app.data.auth.FirebaseAuthRepository
import com.around.app.data.circles.CircleRepository
import com.around.app.data.circles.FirebaseCircleRepository
import com.around.app.data.user.FirebaseUserRepository
import com.around.app.data.user.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FirebaseUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindCircleRepository(impl: FirebaseCircleRepository): CircleRepository
}
